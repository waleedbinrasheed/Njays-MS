package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.master.MasterDataDtos.CustomerRequest;
import com.inventory.dto.master.MasterDataDtos.CustomerResponse;
import com.inventory.entity.Customer;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.MasterDataMapper;
import com.inventory.entity.Representative;
import com.inventory.repository.CustomerRepository;
import com.inventory.repository.RepresentativeRepository;
import com.inventory.repository.SaleRepository;
import com.inventory.repository.SampleIssueRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.inventory.service.MasterDataSupport.checkVersion;
import static com.inventory.service.MasterDataSupport.deleteIfUnused;
import static com.inventory.service.MasterDataSupport.inUse;
import static com.inventory.service.MasterDataSupport.searchTerm;
import static com.inventory.service.MasterDataSupport.trimToNull;

@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final SaleRepository sales;
    private final SampleIssueRepository samples;
    private final RepresentativeRepository representatives;

    public CustomerService(CustomerRepository customers, SaleRepository sales, SampleIssueRepository samples,
                           RepresentativeRepository representatives) {
        this.customers = customers;
        this.sales = sales;
        this.samples = samples;
        this.representatives = representatives;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> search(String search, Boolean active, Long representativeId, boolean unassigned,
                                                 Pageable pageable) {
        return PageResponse.of(customers.search(searchTerm(search), active, representativeId, unassigned, pageable),
                MasterDataMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return MasterDataMapper.toResponse(load(id));
    }

    /** The default customer preselected on new sales. */
    @Transactional(readOnly = true)
    public CustomerResponse walkIn() {
        return customers.findByWalkInTrue()
                .map(MasterDataMapper::toResponse)
                .orElseThrow(() -> new IllegalStateException("Walk-in customer missing; check migrations"));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Customer customer = new Customer();
        customer.setRepresentative(resolveRepresentative(request.representativeId(), null));
        apply(customer, request);
        return MasterDataMapper.toResponse(customers.save(customer));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = load(id);
        checkVersion(customer, request.version());
        if (customer.isWalkIn() && Boolean.FALSE.equals(request.active())) {
            throw new BusinessException("The walk-in customer cannot be deactivated");
        }
        if (!customer.isWalkIn()) {
            customer.setRepresentative(resolveRepresentative(request.representativeId(), customer.getRepresentative()));
        }
        apply(customer, request);
        return MasterDataMapper.toResponse(customers.saveAndFlush(customer));
    }

    @Transactional
    public void delete(Long id) {
        Customer customer = load(id);
        if (customer.isWalkIn()) {
            throw new BusinessException("The walk-in customer cannot be deleted");
        }
        String label = "Customer '" + customer.getName() + "'";
        if (sales.existsByCustomerId(id) || samples.existsByCustomerId(id)) {
            throw inUse(label);
        }
        deleteIfUnused(customers, customer, label);
    }

    private void apply(Customer c, CustomerRequest r) {
        c.setName(r.name().trim());
        c.setPhone(trimToNull(r.phone()));
        c.setEmail(trimToNull(r.email()));
        c.setAddress(trimToNull(r.address()));
        c.setActive(r.active() == null || r.active());
    }

    /**
     * Every customer needs a representative. A newly chosen one must be active; keeping the current
     * (since deactivated) one is allowed so other details can still be edited.
     */
    private Representative resolveRepresentative(Long id, Representative current) {
        if (id == null) {
            throw new BusinessException("Choose the customer's representative");
        }
        if (current != null && current.getId().equals(id)) return current;
        Representative r = representatives.findById(id)
                .orElseThrow(() -> new BusinessException("Selected representative does not exist"));
        if (!r.isActive()) {
            throw new BusinessException("Representative '" + r.getName() + "' is inactive");
        }
        return r;
    }

    private Customer load(Long id) {
        return customers.findWithRepresentativeById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }
}
