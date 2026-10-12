package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.master.MasterDataDtos.Ref;
import com.inventory.dto.sample.SampleDtos.CreateSampleReturnRequest;
import com.inventory.dto.sample.SampleDtos.SampleReturnDetail;
import com.inventory.dto.sample.SampleDtos.SampleReturnLine;
import com.inventory.dto.sample.SampleDtos.SampleReturnLineRequest;
import com.inventory.dto.sample.SampleDtos.SampleReturnSummary;
import com.inventory.entity.ReferenceType;
import com.inventory.entity.SampleIssue;
import com.inventory.entity.SampleIssueItem;
import com.inventory.entity.SampleReturn;
import com.inventory.entity.SampleReturnItem;
import com.inventory.entity.TransactionType;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.InventoryMapper;
import com.inventory.mapper.MasterDataMapper;
import com.inventory.repository.SampleIssueRepository;
import com.inventory.repository.SampleReturnRepository;
import com.inventory.repository.SampleReturnRepository.Filters;
import com.inventory.service.DocumentNumberService.DocumentType;
import com.inventory.service.InventoryService.Movement;
import com.inventory.service.ReturnMath.Returned;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.inventory.service.MasterDataSupport.trimToNull;

/**
 * Sample return: the representative brings back some or all of the samples; stock goes back up and
 * each book is valued at its ORIGINAL sample price. The sample row is locked while a return is
 * recorded, so returns of the same sample are serialised and can never exceed what is still out.
 * Returns are accepted even if the representative or book has since been deactivated.
 */
@Service
public class SampleReturnService {

    private static final Logger log = LoggerFactory.getLogger(SampleReturnService.class);

    private final SampleReturnRepository returns;
    private final SampleIssueRepository samples;
    private final SampleService sampleService;
    private final InventoryService inventoryService;
    private final DocumentNumberService numbers;
    private final UserDirectory userDirectory;
    private final Clock clock;
    private final ZoneId zone;

    public SampleReturnService(SampleReturnRepository returns, SampleIssueRepository samples, SampleService sampleService,
                               InventoryService inventoryService, DocumentNumberService numbers,
                               UserDirectory userDirectory, Clock clock, ZoneId businessZone) {
        this.returns = returns;
        this.samples = samples;
        this.sampleService = sampleService;
        this.inventoryService = inventoryService;
        this.numbers = numbers;
        this.userDirectory = userDirectory;
        this.clock = clock;
        this.zone = businessZone;
    }

    public record SampleReturnFilter(String search, Long sampleId, Long representativeId, LocalDate from, LocalDate to) {
    }

    @Transactional
    public SampleReturnDetail create(CreateSampleReturnRequest request) {
        SampleIssue sample = samples.lockById(request.sampleId())
                .orElseThrow(() -> new BusinessException("Sample does not exist"));
        if (sample.isCancelled()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Sample " + sample.getSampleNumber() + " is cancelled; nothing can be returned");
        }
        if (request.returnDate().isBefore(sample.getIssueDate())) {
            throw new BusinessException("Return date cannot be before the issue date");
        }
        if (request.returnDate().isAfter(LocalDate.now(clock.withZone(zone)))) {
            throw new BusinessException("Return date cannot be in the future");
        }

        Map<Long, SampleIssueItem> lines = sample.getItems().stream()
                .collect(Collectors.toMap(SampleIssueItem::getId, Function.identity()));
        Map<Long, Returned> already = sampleService.returnedPerLine(sample.getId());

        SampleReturn ret = new SampleReturn(numbers.next(DocumentType.SAMPLE_RETURN), sample, request.returnDate(),
                trimToNull(request.notes()));
        Set<Long> seen = new HashSet<>();
        for (SampleReturnLineRequest line : request.items()) {
            SampleIssueItem issued = lines.get(line.sampleItemId());
            if (issued == null) {
                throw new BusinessException("Line " + line.sampleItemId() + " is not part of " + sample.getSampleNumber());
            }
            if (!seen.add(issued.getId())) {
                throw new BusinessException("'" + issued.getProduct().getTitle() + "' appears more than once in this return");
            }
            int outstanding = issued.getQuantity() - already.getOrDefault(issued.getId(), Returned.NONE).quantity();
            if (line.quantity() > outstanding) {
                throw new BusinessException("Cannot return " + line.quantity() + " of '" + issued.getProduct().getTitle()
                        + "': only " + outstanding + " of " + issued.getQuantity() + " still out on " + sample.getSampleNumber());
            }
            ret.addItem(new SampleReturnItem(issued, line.quantity()));
        }

        SampleReturn saved = returns.saveAndFlush(ret);
        inventoryService.applyAll(saved.getItems().stream()
                .map(i -> new Movement(i.getProduct().getId(), TransactionType.SAMPLE_RETURN, i.getQuantity(),
                        ReferenceType.SAMPLE_RETURN, saved.getId(), saved.getReturnNumber() + " (" + sample.getSampleNumber() + ")"))
                .toList());
        sample.recordReturn(saved.getTotalQuantity(), saved.getTotal());
        samples.saveAndFlush(sample);

        log.info("Sample return {} recorded against {}: {} units, value {}", saved.getReturnNumber(),
                sample.getSampleNumber(), saved.getTotalQuantity(), saved.getTotal());
        return detail(saved);
    }

    @Transactional(readOnly = true)
    public SampleReturnDetail get(Long id) {
        return detail(returns.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Sample return", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<SampleReturnSummary> search(SampleReturnFilter f, Pageable pageable) {
        if (f.from() != null && f.to() != null && f.to().isBefore(f.from())) {
            throw new BusinessException("'To' date must not be before 'from' date");
        }
        Specification<SampleReturn> spec = Specification.allOf(Filters.matches(f.search()), Filters.sample(f.sampleId()),
                Filters.representative(f.representativeId()), Filters.dateFrom(f.from()), Filters.dateTo(f.to()));
        Page<SampleReturn> page = returns.findAll(spec, pageable);
        Map<Long, Ref> creators = userDirectory.refs(page.getContent().stream().map(SampleReturn::getCreatedBy).toList());
        return PageResponse.of(page, r -> {
            SampleIssue s = r.getSampleIssue();
            return new SampleReturnSummary(r.getId(), r.getReturnNumber(), r.getReturnDate(), s.getId(), s.getSampleNumber(),
                    new Ref(s.getRepresentative().getId(), s.getRepresentative().getName()), SampleService.customerRef(s),
                    r.getTotalQuantity(),
                    r.getTotal(), creators.get(r.getCreatedBy()), r.getCreatedAt());
        });
    }

    private SampleReturnDetail detail(SampleReturn r) {
        SampleIssue s = r.getSampleIssue();
        List<SampleReturnLine> lines = r.getItems().stream()
                .map(i -> new SampleReturnLine(i.getId(), i.getSampleIssueItem().getId(), InventoryMapper.productRef(i.getProduct()),
                        i.getQuantity(), i.getUnitPrice(), i.getLineTotal()))
                .toList();
        return new SampleReturnDetail(r.getId(), r.getReturnNumber(), r.getReturnDate(), s.getId(), s.getSampleNumber(),
                s.getIssueDate(), MasterDataMapper.ref(s.getRepresentative()), SampleService.customerRef(s),
                r.getTotalQuantity(), r.getTotal(),
                r.getNotes(), lines, userDirectory.ref(r.getCreatedBy()), r.getCreatedAt());
    }
}
