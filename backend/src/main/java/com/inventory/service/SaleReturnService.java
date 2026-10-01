package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.master.MasterDataDtos.Ref;
import com.inventory.dto.returns.ReturnDtos.CreateReturnRequest;
import com.inventory.dto.returns.ReturnDtos.ReturnDetail;
import com.inventory.dto.returns.ReturnDtos.ReturnLine;
import com.inventory.dto.returns.ReturnDtos.ReturnLineRequest;
import com.inventory.dto.returns.ReturnDtos.ReturnSummary;
import com.inventory.dto.returns.ReturnDtos.ReturnableLine;
import com.inventory.entity.Sale;
import com.inventory.entity.SaleItem;
import com.inventory.entity.ReferenceType;
import com.inventory.entity.SaleReturn;
import com.inventory.entity.SaleReturnItem;
import com.inventory.entity.TransactionType;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.InventoryMapper;
import com.inventory.repository.SaleRepository;
import com.inventory.repository.SaleReturnRepository;
import com.inventory.repository.SaleReturnRepository.Filters;
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
 * Sales return: the representative brings back some or all items of an invoice; stock goes back up.
 * The original sale row is locked while a return is recorded, so two returns of the same
 * sale are serialised and together can never exceed what was on it.
 */
@Service
public class SaleReturnService {

    private static final Logger log = LoggerFactory.getLogger(SaleReturnService.class);

    private final SaleReturnRepository returns;
    private final SaleRepository documents;
    private final InventoryService inventoryService;
    private final DocumentNumberService numbers;
    private final UserDirectory userDirectory;
    private final Clock clock;
    private final ZoneId zone;

    public SaleReturnService(SaleReturnRepository returns, SaleRepository documents, InventoryService inventoryService,
                      DocumentNumberService numbers, UserDirectory userDirectory, Clock clock, ZoneId businessZone) {
        this.returns = returns;
        this.documents = documents;
        this.inventoryService = inventoryService;
        this.numbers = numbers;
        this.userDirectory = userDirectory;
        this.clock = clock;
        this.zone = businessZone;
    }

    public record ReturnFilter(String search, Long documentId, LocalDate from, LocalDate to) {
    }

    @Transactional
    public ReturnDetail create(CreateReturnRequest request) {
        Sale document = documents.lockById(request.documentId())
                .orElseThrow(() -> new BusinessException("Invoice does not exist"));
        if (document.isCancelled()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Invoice " + document.getInvoiceNumber() + " is cancelled; nothing can be returned");
        }
        if (request.returnDate().isBefore(document.getSaleDate())) {
            throw new BusinessException("Return date cannot be before the sale date");
        }
        if (request.returnDate().isAfter(LocalDate.now(clock.withZone(zone)))) {
            throw new BusinessException("Return date cannot be in the future");
        }

        Map<Long, SaleItem> lines = document.getItems().stream()
                .collect(Collectors.toMap(SaleItem::getId, Function.identity()));
        Map<Long, Returned> already = returnedPerLine(document.getId());

        SaleReturn ret = new SaleReturn(numbers.next(DocumentType.SALE_RETURN), document, request.returnDate(),
                request.reason().trim(), trimToNull(request.notes()));
        Set<Long> seen = new HashSet<>();
        for (ReturnLineRequest line : request.items()) {
            SaleItem original = lines.get(line.documentItemId());
            if (original == null) {
                throw new BusinessException("Line " + line.documentItemId() + " is not part of " + document.getInvoiceNumber());
            }
            if (!seen.add(original.getId())) {
                throw new BusinessException("'" + original.getProduct().getTitle() + "' appears more than once in this return");
            }
            BigDecimal amount = ReturnMath.amountFor(original.getQuantity(), original.getLineTotal(),
                    already.getOrDefault(original.getId(), Returned.NONE), line.quantity(), original.getProduct().getTitle());
            ret.addItem(new SaleReturnItem(original, line.quantity(), amount));
        }

        SaleReturn saved = returns.saveAndFlush(ret);
        inventoryService.applyAll(saved.getItems().stream()
                .map(i -> new Movement(i.getProduct().getId(), TransactionType.SALE_RETURN, i.getQuantity(),
                        ReferenceType.SALE_RETURN, saved.getId(), saved.getReturnNumber() + " (" + document.getInvoiceNumber() + ")"))
                .toList());

        log.info("Sales return {} recorded against {}: total {}", saved.getReturnNumber(), document.getInvoiceNumber(), saved.getTotal());
        return detail(saved);
    }

    /** Lines of the original sale with how much has been and can still be returned. */
    @Transactional(readOnly = true)
    public List<ReturnableLine> returnable(Long documentId) {
        Sale document = documents.findDetailedById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", documentId));
        Map<Long, Returned> already = returnedPerLine(documentId);
        return document.getItems().stream().map(i -> {
            Returned r = already.getOrDefault(i.getId(), Returned.NONE);
            int returnable = document.isCancelled() ? 0 : i.getQuantity() - r.quantity();
            return new ReturnableLine(i.getId(), i.getLineNo(), InventoryMapper.productRef(i.getProduct()),
                    i.getQuantity(), r.quantity(), returnable, i.getLineTotal(), r.amount());
        }).toList();
    }

    @Transactional(readOnly = true)
    public ReturnDetail get(Long id) {
        return detail(returns.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Sales return", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReturnSummary> search(ReturnFilter f, Pageable pageable) {
        if (f.from() != null && f.to() != null && f.to().isBefore(f.from())) {
            throw new BusinessException("'To' date must not be before 'from' date");
        }
        Specification<SaleReturn> spec = Specification.allOf(Filters.matches(f.search()), Filters.document(f.documentId()),
                Filters.dateFrom(f.from()), Filters.dateTo(f.to()));
        Page<SaleReturn> page = returns.findAll(spec, pageable);
        Map<Long, Ref> creators = userDirectory.refs(page.getContent().stream().map(SaleReturn::getCreatedBy).toList());
        return PageResponse.of(page, r -> new ReturnSummary(r.getId(), r.getReturnNumber(), r.getReturnDate(),
                r.getSale().getId(), r.getSale().getInvoiceNumber(), partyOf(r.getSale()), r.getTotal(),
                creators.get(r.getCreatedBy()), r.getCreatedAt()));
    }

    private ReturnDetail detail(SaleReturn r) {
        Sale d = r.getSale();
        List<ReturnLine> lines = r.getItems().stream()
                .map(i -> new ReturnLine(i.getId(), i.getSaleItem().getId(), InventoryMapper.productRef(i.getProduct()),
                        i.getQuantity(), i.getLineTotal()))
                .toList();
        return new ReturnDetail(r.getId(), r.getReturnNumber(), r.getReturnDate(), d.getId(), d.getInvoiceNumber(),
                d.getSaleDate(), partyOf(d), r.getTotal(), r.getReason(), r.getNotes(), lines,
                userDirectory.ref(r.getCreatedBy()), r.getCreatedAt());
    }

    /** The buyer credited by the return: the representative, or the customer on pre-representative invoices. */
    private static Ref partyOf(Sale d) {
        if (d.getRepresentative() != null) {
            return new Ref(d.getRepresentative().getId(), d.getRepresentative().getName());
        }
        return new Ref(d.getCustomer().getId(), d.getCustomer().getName());
    }

    private Map<Long, Returned> returnedPerLine(Long documentId) {
        Map<Long, Returned> map = new HashMap<>();
        for (Object[] row : returns.returnedPerLine(documentId)) {
            map.put((Long) row[0], new Returned(((Number) row[1]).intValue(), (BigDecimal) row[2]));
        }
        return map;
    }
}
