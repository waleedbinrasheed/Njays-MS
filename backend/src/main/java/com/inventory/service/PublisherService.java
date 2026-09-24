package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.master.MasterDataDtos.PartyRequest;
import com.inventory.dto.master.MasterDataDtos.PartyResponse;
import com.inventory.entity.Publisher;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.MasterDataMapper;
import com.inventory.repository.ProductRepository;
import com.inventory.repository.PublisherRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.inventory.service.MasterDataSupport.checkVersion;
import static com.inventory.service.MasterDataSupport.inUse;
import static com.inventory.service.MasterDataSupport.searchTerm;
import static com.inventory.service.MasterDataSupport.trimToNull;

@Service
public class PublisherService {

    private final PublisherRepository publishers;
    private final ProductRepository products;

    public PublisherService(PublisherRepository publishers, ProductRepository products) {
        this.publishers = publishers;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public PageResponse<PartyResponse> search(String search, Boolean active, Pageable pageable) {
        return PageResponse.of(publishers.search(searchTerm(search), active, pageable), MasterDataMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PartyResponse get(Long id) {
        return MasterDataMapper.toResponse(load(id));
    }

    @Transactional
    public PartyResponse create(PartyRequest request) {
        String name = request.name().trim();
        if (publishers.existsByNameIgnoreCase(name)) {
            throw duplicate(name);
        }
        Publisher publisher = new Publisher();
        apply(publisher, request, name);
        return MasterDataMapper.toResponse(publishers.save(publisher));
    }

    @Transactional
    public PartyResponse update(Long id, PartyRequest request) {
        Publisher publisher = load(id);
        checkVersion(publisher, request.version());
        String name = request.name().trim();
        if (publishers.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicate(name);
        }
        apply(publisher, request, name);
        return MasterDataMapper.toResponse(publishers.saveAndFlush(publisher));
    }

    @Transactional
    public void delete(Long id) {
        Publisher publisher = load(id);
        if (products.countByPublisherId(id) > 0) {
            throw inUse("Publisher '" + publisher.getName() + "'");
        }
        publishers.delete(publisher);
    }

    private void apply(Publisher p, PartyRequest r, String name) {
        p.setName(name);
        p.setContactPerson(trimToNull(r.contactPerson()));
        p.setPhone(trimToNull(r.phone()));
        p.setEmail(trimToNull(r.email()));
        p.setAddress(trimToNull(r.address()));
        p.setActive(r.active() == null || r.active());
    }

    private Publisher load(Long id) {
        return publishers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Publisher", id));
    }

    private static BusinessException duplicate(String name) {
        return new BusinessException(HttpStatus.CONFLICT, "A publisher named '" + name + "' already exists");
    }
}
