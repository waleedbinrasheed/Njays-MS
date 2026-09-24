package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.master.MasterDataDtos.Ref;
import com.inventory.dto.returns.ReturnDtos.CreateReturnRequest;
import com.inventory.dto.returns.ReturnDtos.ReturnDetail;
import com.inventory.dto.returns.ReturnDtos.ReturnLine;
import com.inventory.dto.returns.ReturnDtos.ReturnLineRequest;
import com.inventory.dto.returns.ReturnDtos.ReturnSummary;
import com.inventory.dto.returns.ReturnDtos.ReturnableLine;
import com.inventory.entity.Purchase;
import com.inventory.entity.PurchaseItem;
import com.inventory.entity.ReferenceType;
import com.inventory.entity.PurchaseReturn;
import com.inventory.entity.PurchaseReturnItem;
import com.inventory.entity.TransactionType;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.InventoryMapper;
import com.inventory.repository.PurchaseRepository;
import com.inventory.repository.PurchaseReturnRepository;
import com.inventory.repository.PurchaseReturnRepository.Filters;
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
 * Purchase return: items are sent back to the supplier; stock goes down (refused if it is no longer on hand).
 * The original purchase row is locked while a return is recorded, so two returns of the same
 * purchase are serialised and together can never exceed what was on it.
 */
@Service
public class PurchaseReturnService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseReturnService.class);

    private final PurchaseReturnRepository returns;
    private final PurchaseRepository documents;
    private final InventoryService inventoryService;
    private final DocumentNumberService numbers;
    private final UserDirectory userDirectory;
    private final Clock clock;
    private final ZoneId zone;

    public PurchaseReturnService(PurchaseReturnRepository returns, PurchaseRepository documents, InventoryService inventoryService,
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
        Purchase document = documents.lockById(request.documentId())
                .orElseThrow(() -> new BusinessException("Purchase does not exist"));
        if (document.isCancelled()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Purchase " + document.getPurchaseNumber() + " is cancelled; nothing can be returned");
        }
        if (request.returnDate().isBefore(document.getPurchaseDate())) {
            throw new BusinessException("Return date cannot be before the purchase date");
        }
        if (request.returnDate().isAfter(LocalDate.now(clock.withZone(zone)))) {
            throw new BusinessException("Return date cannot be in the future");
        }

        Map<Long, PurchaseItem> lines = document.getItems().stream()
                .collect(Collectors.toMap(PurchaseItem::getId, Function.identity()));
        Map<Long, Returned> already = returnedPerLine(document.getId());

        PurchaseReturn ret = new PurchaseReturn(numbers.next(DocumentType.PURCHASE_RETURN), document, request.returnDate(),
                request.reason().trim(), trimToNull(request.notes()));
        Set<Long> seen = new HashSet<>();
        for (ReturnLineRequest line : request.items()) {
            PurchaseItem original = lines.get(line.documentItemId());
            if (original == null) {
                throw new BusinessException("Line " + line.documentItemId() + " is not part of " + document.getPurchaseNumber());
            }
            if (!seen.add(original.getId())) {
                throw new BusinessException("'" + original.getProduct().getTitle() + "' appears more than once in this return");
            }
            BigDecimal amount = ReturnMath.amountFor(original.getQuantity(), original.getLineTotal(),
                    already.getOrDefault(original.getId(), Returned.NONE), line.quantity(), original.getProduct().getTitle());
            ret.addItem(new PurchaseReturnItem(original, line.quantity(), amount));
        }

        PurchaseReturn saved = returns.saveAndFlush(ret);
        inventoryService.applyAll(saved.getItems().stream()
                .map(i -> new Movement(i.getProduct().getId(), TransactionType.PURCHASE_RETURN, -i.getQuantity(),
                        ReferenceType.PURCHASE_RETURN, saved.getId(), saved.getReturnNumber() + " (" + document.getPurchaseNumber() + ")"))
                .toList());

        log.info("Purchase return {} recorded against {}: total {}", saved.getReturnNumber(), document.getPurchaseNumber(), saved.getTotal());
        return detail(saved);
    }

    /** Lines of the original purchase with how much has been and can still be returned. */
    @Transactional(readOnly = true)
    public List<ReturnableLine> returnable(Long documentId) {
        Purchase document = documents.findDetailedById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase", documentId));
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
        return detail(returns.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Purchase return", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReturnSummary> search(ReturnFilter f, Pageable pageable) {
        if (f.from() != null && f.to() != null && f.to().isBefore(f.from())) {
            throw new BusinessException("'To' date must not be before 'from' date");
        }
        Specification<PurchaseReturn> spec = Specification.allOf(Filters.matches(f.search()), Filters.document(f.documentId()),
                Filters.dateFrom(f.from()), Filters.dateTo(f.to()));
        Page<PurchaseReturn> page = returns.findAll(spec, pageable);
        Map<Long, Ref> creators = userDirectory.refs(page.getContent().stream().map(PurchaseReturn::getCreatedBy).toList());
        return PageResponse.of(page, r -> new ReturnSummary(r.getId(), r.getReturnNumber(), r.getReturnDate(),
                r.getPurchase().getId(), r.getPurchase().getPurchaseNumber(), partyOf(r.getPurchase()), r.getTotal(),
                creators.get(r.getCreatedBy()), r.getCreatedAt()));
    }

    private ReturnDetail detail(PurchaseReturn r) {
        Purchase d = r.getPurchase();
        List<ReturnLine> lines = r.getItems().stream()
                .map(i -> new ReturnLine(i.getId(), i.getPurchaseItem().getId(), InventoryMapper.productRef(i.getProduct()),
                        i.getQuantity(), i.getLineTotal()))
                .toList();
        return new ReturnDetail(r.getId(), r.getReturnNumber(), r.getReturnDate(), d.getId(), d.getPurchaseNumber(),
                d.getPurchaseDate(), partyOf(d), r.getTotal(), r.getReason(), r.getNotes(), lines,
                userDirectory.ref(r.getCreatedBy()), r.getCreatedAt());
    }

    private static Ref partyOf(Purchase d) {
        return new Ref(d.getSupplier().getId(), d.getSupplier().getName());
    }

    private Map<Long, Returned> returnedPerLine(Long documentId) {
        Map<Long, Returned> map = new HashMap<>();
        for (Object[] row : returns.returnedPerLine(documentId)) {
            map.put((Long) row[0], new Returned(((Number) row[1]).intValue(), (BigDecimal) row[2]));
        }
        return map;
    }
}
