package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.document.DocumentDtos.CancelRequest;
import com.inventory.dto.master.MasterDataDtos.Ref;
import com.inventory.dto.sample.SampleDtos.CreateSampleRequest;
import com.inventory.dto.sample.SampleDtos.SampleDetail;
import com.inventory.dto.sample.SampleDtos.SampleLine;
import com.inventory.dto.sample.SampleDtos.SampleLineRequest;
import com.inventory.dto.sample.SampleDtos.SampleReturnRef;
import com.inventory.dto.sample.SampleDtos.SampleSummary;
import com.inventory.entity.Product;
import com.inventory.entity.ReferenceType;
import com.inventory.entity.Representative;
import com.inventory.entity.SampleIssue;
import com.inventory.entity.SampleIssueItem;
import com.inventory.entity.SampleStatus;
import com.inventory.entity.TransactionType;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.InventoryMapper;
import com.inventory.mapper.MasterDataMapper;
import com.inventory.repository.AppUserRepository;
import com.inventory.repository.ProductRepository;
import com.inventory.repository.RepresentativeRepository;
import com.inventory.repository.SampleIssueRepository;
import com.inventory.repository.SampleIssueRepository.Filters;
import com.inventory.repository.SampleReturnRepository;
import com.inventory.security.CurrentUser;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.inventory.service.MasterDataSupport.trimToNull;

/**
 * Sample issues: books handed to a representative as a "SAMPLE SALE" at a sample price (by default
 * the book's purchase price, never above its selling price). The document and its stock movements
 * are saved in ONE transaction; stock is checked while each product row is locked. Samples are kept
 * apart from sales and never counted in sales figures.
 */
@Service
public class SampleService {

    private static final Logger log = LoggerFactory.getLogger(SampleService.class);

    private final SampleIssueRepository samples;
    private final SampleReturnRepository sampleReturns;
    private final RepresentativeRepository representatives;
    private final ProductRepository products;
    private final AppUserRepository users;
    private final InventoryService inventoryService;
    private final DocumentNumberService numbers;
    private final UserDirectory userDirectory;
    private final Clock clock;
    private final ZoneId zone;

    public SampleService(SampleIssueRepository samples, SampleReturnRepository sampleReturns,
                         RepresentativeRepository representatives, ProductRepository products, AppUserRepository users,
                         InventoryService inventoryService, DocumentNumberService numbers, UserDirectory userDirectory,
                         Clock clock, ZoneId businessZone) {
        this.samples = samples;
        this.sampleReturns = sampleReturns;
        this.representatives = representatives;
        this.products = products;
        this.users = users;
        this.inventoryService = inventoryService;
        this.numbers = numbers;
        this.userDirectory = userDirectory;
        this.clock = clock;
        this.zone = businessZone;
    }

    public record SampleFilter(String search, Long representativeId, Long productId, LocalDate from, LocalDate to,
                               SampleStatus status) {
    }

    @Transactional
    public SampleDetail create(CreateSampleRequest request) {
        if (request.issueDate().isAfter(LocalDate.now(clock.withZone(zone)))) {
            throw new BusinessException("Issue date cannot be in the future");
        }
        Representative representative = resolveRepresentative(request.representativeId());
        Map<Long, Product> productsById = loadProducts(request.items());

        SampleIssue sample = new SampleIssue(numbers.next(DocumentType.SAMPLE), representative, request.issueDate(),
                trimToNull(request.notes()));
        for (SampleLineRequest line : request.items()) {
            Product product = productsById.get(line.productId());
            if (line.unitPrice().compareTo(product.getSellingPrice()) > 0) {
                throw new BusinessException("Sample price of '" + product.getTitle() + "' (" + line.unitPrice().toPlainString()
                        + ") cannot be above its selling price (" + product.getSellingPrice().toPlainString() + ")");
            }
            sample.addItem(new SampleIssueItem(product, line.quantity(), line.unitPrice()));
        }

        SampleIssue saved = samples.saveAndFlush(sample);
        // Throws "Insufficient stock for '...': available X, requested Y" and rolls everything back.
        inventoryService.applyAll(saved.getItems().stream()
                .map(i -> new Movement(i.getProduct().getId(), TransactionType.SAMPLE_ISSUE, -i.getQuantity(),
                        ReferenceType.SAMPLE_ISSUE, saved.getId(), saved.getSampleNumber() + " to " + representative.getName()))
                .toList());

        log.info("Sample {} issued to representative id {}: {} units, value {}", saved.getSampleNumber(),
                representative.getId(), saved.getTotalQuantity(), saved.getTotal());
        return detail(saved);
    }

    /**
     * Puts every issued book back in stock. Refused once anything has been returned (record a return
     * for the rest instead), so stock can never come back twice.
     */
    @Transactional
    public SampleDetail cancel(Long id, CancelRequest request) {
        SampleIssue sample = samples.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Sample", id));
        if (sample.isCancelled()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Sample " + sample.getSampleNumber() + " is already cancelled");
        }
        if (sample.getReturnedQuantity() > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "Sample " + sample.getSampleNumber()
                    + " has returns and cannot be cancelled. Record a return for the remaining books instead");
        }
        String reason = request.reason().trim();
        inventoryService.applyAll(sample.getItems().stream()
                .map(i -> new Movement(i.getProduct().getId(), TransactionType.SAMPLE_CANCEL, i.getQuantity(),
                        ReferenceType.SAMPLE_ISSUE, sample.getId(), "Cancelled " + sample.getSampleNumber() + ": " + reason))
                .toList());
        sample.cancel(users.getReferenceById(CurrentUser.requireId()), Instant.now(clock), reason);
        log.info("Sample {} cancelled by user id {}", sample.getSampleNumber(), CurrentUser.requireId());
        return detail(samples.saveAndFlush(sample));
    }

    @Transactional(readOnly = true)
    public SampleDetail get(Long id) {
        return detail(samples.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Sample", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<SampleSummary> search(SampleFilter f, Pageable pageable) {
        if (f.from() != null && f.to() != null && f.to().isBefore(f.from())) {
            throw new BusinessException("'To' date must not be before 'from' date");
        }
        Specification<SampleIssue> spec = Specification.allOf(
                Filters.matches(f.search()), Filters.representative(f.representativeId()), Filters.product(f.productId()),
                Filters.dateFrom(f.from()), Filters.dateTo(f.to()), Filters.status(f.status()));
        Page<SampleIssue> page = samples.findAll(spec, pageable);
        Map<Long, Ref> creators = userDirectory.refs(page.getContent().stream().map(SampleIssue::getCreatedBy).toList());
        return PageResponse.of(page, s -> new SampleSummary(s.getId(), s.getSampleNumber(), s.getIssueDate(),
                MasterDataMapper.ref(s.getRepresentative()), s.getTotalQuantity(), s.getTotal(), s.getReturnedQuantity(),
                s.getReturnedTotal(), s.getOutstandingQuantity(), s.getOutstandingTotal(), s.getStatus(),
                creators.get(s.getCreatedBy()), s.getCreatedAt()));
    }

    /** Called inside a transaction, so lazy lines/products load as needed. */
    private SampleDetail detail(SampleIssue s) {
        Map<Long, Returned> returned = returnedPerLine(s.getId());
        List<SampleLine> lines = s.getItems().stream().map(i -> {
            Returned r = returned.getOrDefault(i.getId(), Returned.NONE);
            int outQty = s.isCancelled() ? 0 : i.getQuantity() - r.quantity();
            BigDecimal outAmount = s.isCancelled() ? BigDecimal.ZERO : i.getLineTotal().subtract(r.amount());
            return new SampleLine(i.getId(), i.getLineNo(), InventoryMapper.productRef(i.getProduct()), i.getQuantity(),
                    i.getUnitPrice(), i.getLineTotal(), r.quantity(), r.amount(), outQty, outAmount);
        }).toList();
        List<SampleReturnRef> returns = sampleReturns.findBySample(s.getId()).stream()
                .map(r -> new SampleReturnRef(r.getId(), r.getReturnNumber(), r.getReturnDate(), r.getTotalQuantity(), r.getTotal()))
                .toList();
        return new SampleDetail(s.getId(), s.getSampleNumber(), s.getIssueDate(), MasterDataMapper.ref(s.getRepresentative()),
                s.getRepresentative().getAddress(), s.getTotalQuantity(), s.getTotal(), s.getReturnedQuantity(),
                s.getReturnedTotal(), s.getOutstandingQuantity(), s.getOutstandingTotal(), s.getStatus(), s.getNotes(),
                lines, returns, userDirectory.ref(s.getCreatedBy()), s.getCreatedAt(), s.getCancelledAt(),
                s.getCancelledBy() == null ? null : InventoryMapper.userRef(s.getCancelledBy()), s.getCancelReason());
    }

    Map<Long, Returned> returnedPerLine(Long sampleId) {
        Map<Long, Returned> map = new HashMap<>();
        for (Object[] row : sampleReturns.returnedPerLine(sampleId)) {
            map.put((Long) row[0], new Returned(((Number) row[1]).intValue(), (BigDecimal) row[2]));
        }
        return map;
    }

    /** An existing, active, real representative (not the built-in Counter Sales one). */
    private Representative resolveRepresentative(Long representativeId) {
        Representative representative = representatives.findById(representativeId)
                .orElseThrow(() -> new BusinessException("Selected representative does not exist"));
        if (representative.isBuiltIn()) {
            throw new BusinessException("Samples cannot be issued to '" + representative.getName()
                    + "'; choose the representative who takes the samples");
        }
        if (!representative.isActive()) {
            throw new BusinessException("Representative '" + representative.getName() + "' is inactive");
        }
        return representative;
    }

    /** All books must exist, be active, and appear on only one line. */
    private Map<Long, Product> loadProducts(List<SampleLineRequest> items) {
        Set<Long> seen = new HashSet<>();
        for (SampleLineRequest line : items) {
            if (!seen.add(line.productId())) {
                throw new BusinessException("The same book appears on more than one line; combine them into one line");
            }
        }
        Map<Long, Product> byId = products.findAllById(seen).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (SampleLineRequest line : items) {
            Product product = byId.get(line.productId());
            if (product == null) {
                throw new BusinessException("Product " + line.productId() + " does not exist");
            }
            if (!product.isActive()) {
                throw new BusinessException("Product '" + product.getTitle() + "' is inactive and cannot be issued as a sample");
            }
        }
        return byId;
    }
}
