package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.master.MasterDataDtos.MoveCustomersRequest;
import com.inventory.dto.master.MasterDataDtos.MoveCustomersResponse;
import com.inventory.dto.master.MasterDataDtos.RepresentativeRequest;
import com.inventory.dto.master.MasterDataDtos.RepresentativeResponse;
import com.inventory.entity.Representative;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.MasterDataMapper;
import com.inventory.repository.CustomerRepository;
import com.inventory.repository.RepresentativeRepository;
import com.inventory.repository.SaleRepository;
import com.inventory.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static com.inventory.service.MasterDataSupport.checkVersion;
import static com.inventory.service.MasterDataSupport.inUse;
import static com.inventory.service.MasterDataSupport.searchTerm;
import static com.inventory.service.MasterDataSupport.trimToNull;

/**
 * Representatives buy from the shop for their own customers. A representative with customers or
 * sales is never deleted (deactivate instead); inactive representatives cannot be given customers
 * or sold to. The built-in "Counter Sales" representative is always active and cannot be deleted.
 */
@Service
public class RepresentativeService {

    private static final Logger log = LoggerFactory.getLogger(RepresentativeService.class);

    private final RepresentativeRepository representatives;
    private final CustomerRepository customers;
    private final SaleRepository sales;

    public RepresentativeService(RepresentativeRepository representatives, CustomerRepository customers,
                                 SaleRepository sales) {
        this.representatives = representatives;
        this.customers = customers;
        this.sales = sales;
    }

    @Transactional(readOnly = true)
    public PageResponse<RepresentativeResponse> search(String search, String area, Boolean active, Pageable pageable) {
        Page<Representative> page = representatives.search(searchTerm(search), searchTerm(area), active, pageable);
        Map<Long, Long> counts = new HashMap<>();
        if (!page.isEmpty()) {
            for (Object[] row : customers.countPerRepresentative(page.map(Representative::getId).getContent())) {
                counts.put((Long) row[0], (Long) row[1]);
            }
        }
        return PageResponse.of(page, r -> MasterDataMapper.toResponse(r, counts.getOrDefault(r.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public RepresentativeResponse get(Long id) {
        Representative r = load(id);
        return MasterDataMapper.toResponse(r, customers.countByRepresentativeId(id));
    }

    /** The built-in representative preselected for counter sales. */
    @Transactional(readOnly = true)
    public RepresentativeResponse counter() {
        Representative r = representatives.findByBuiltInTrue()
                .orElseThrow(() -> new IllegalStateException("Counter Sales representative missing; check migrations"));
        return MasterDataMapper.toResponse(r, customers.countByRepresentativeId(r.getId()));
    }

    @Transactional
    public RepresentativeResponse create(RepresentativeRequest request) {
        String name = request.name().trim();
        if (representatives.existsByNameIgnoreCase(name)) {
            throw duplicate(name);
        }
        Representative r = new Representative();
        apply(r, request, name);
        return MasterDataMapper.toResponse(representatives.save(r), 0);
    }

    @Transactional
    public RepresentativeResponse update(Long id, RepresentativeRequest request) {
        Representative r = load(id);
        checkVersion(r, request.version());
        String name = request.name().trim();
        if (representatives.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicate(name);
        }
        if (r.isBuiltIn() && Boolean.FALSE.equals(request.active())) {
            throw new BusinessException("The Counter Sales representative cannot be deactivated");
        }
        apply(r, request, name);
        return MasterDataMapper.toResponse(representatives.saveAndFlush(r), customers.countByRepresentativeId(id));
    }

    @Transactional
    public void delete(Long id) {
        Representative r = load(id);
        String label = "Representative '" + r.getName() + "'";
        if (r.isBuiltIn()) {
            throw new BusinessException("The Counter Sales representative cannot be deleted");
        }
        if (customers.countByRepresentativeId(id) > 0 || sales.existsByRepresentativeId(id)) {
            throw inUse(label);
        }
        representatives.delete(r);
    }

    /** Moves all of a representative's customers to another (active) representative in one step. */
    @Transactional
    public MoveCustomersResponse moveCustomers(Long fromId, MoveCustomersRequest request) {
        Representative from = load(fromId);
        Representative to = representatives.findById(request.targetRepresentativeId())
                .orElseThrow(() -> new BusinessException("Selected representative does not exist"));
        if (from.getId().equals(to.getId())) {
            throw new BusinessException("Choose a different representative to move the customers to");
        }
        if (!to.isActive()) {
            throw new BusinessException("Representative '" + to.getName() + "' is inactive");
        }
        int moved = customers.moveRepresentative(fromId, to.getId(), CurrentUser.requireId());
        log.info("{} customer(s) moved from representative {} to {} by user id {}", moved, fromId, to.getId(),
                CurrentUser.requireId());
        return new MoveCustomersResponse(moved, MasterDataMapper.ref(from), MasterDataMapper.ref(to));
    }

    private void apply(Representative r, RepresentativeRequest req, String name) {
        r.setName(name);
        r.setPhone(trimToNull(req.phone()));
        r.setEmail(trimToNull(req.email()));
        r.setAddress(trimToNull(req.address()));
        r.setArea(trimToNull(req.area()));
        r.setActive(req.active() == null || req.active());
    }

    private Representative load(Long id) {
        return representatives.findById(id).orElseThrow(() -> new ResourceNotFoundException("Representative", id));
    }

    private static BusinessException duplicate(String name) {
        return new BusinessException(HttpStatus.CONFLICT, "A representative named '" + name + "' already exists");
    }
}
