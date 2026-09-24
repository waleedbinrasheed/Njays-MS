package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.master.MasterDataDtos.PartyRequest;
import com.inventory.dto.master.MasterDataDtos.PartyResponse;
import com.inventory.entity.Supplier;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.MasterDataMapper;
import com.inventory.repository.SupplierRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.inventory.service.MasterDataSupport.checkVersion;
import static com.inventory.service.MasterDataSupport.deleteIfUnused;
import static com.inventory.service.MasterDataSupport.searchTerm;
import static com.inventory.service.MasterDataSupport.trimToNull;

@Service
public class SupplierService {

    private final SupplierRepository suppliers;

    public SupplierService(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @Transactional(readOnly = true)
    public PageResponse<PartyResponse> search(String search, Boolean active, Pageable pageable) {
        return PageResponse.of(suppliers.search(searchTerm(search), active, pageable), MasterDataMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PartyResponse get(Long id) {
        return MasterDataMapper.toResponse(load(id));
    }

    @Transactional
    public PartyResponse create(PartyRequest request) {
        String name = request.name().trim();
        if (suppliers.existsByNameIgnoreCase(name)) {
            throw duplicate(name);
        }
        Supplier supplier = new Supplier();
        apply(supplier, request, name);
        return MasterDataMapper.toResponse(suppliers.save(supplier));
    }

    @Transactional
    public PartyResponse update(Long id, PartyRequest request) {
        Supplier supplier = load(id);
        checkVersion(supplier, request.version());
        String name = request.name().trim();
        if (suppliers.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicate(name);
        }
        apply(supplier, request, name);
        return MasterDataMapper.toResponse(suppliers.saveAndFlush(supplier));
    }

    /** Suppliers with purchases are protected by foreign keys (Phase 5). */
    @Transactional
    public void delete(Long id) {
        Supplier supplier = load(id);
        deleteIfUnused(suppliers, supplier, "Supplier '" + supplier.getName() + "'");
    }

    private void apply(Supplier s, PartyRequest r, String name) {
        s.setName(name);
        s.setContactPerson(trimToNull(r.contactPerson()));
        s.setPhone(trimToNull(r.phone()));
        s.setEmail(trimToNull(r.email()));
        s.setAddress(trimToNull(r.address()));
        s.setActive(r.active() == null || r.active());
    }

    private Supplier load(Long id) {
        return suppliers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
    }

    private static BusinessException duplicate(String name) {
        return new BusinessException(HttpStatus.CONFLICT, "A supplier named '" + name + "' already exists");
    }
}
