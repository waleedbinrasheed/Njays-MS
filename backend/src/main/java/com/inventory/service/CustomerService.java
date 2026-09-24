package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.master.MasterDataDtos.CustomerRequest;
import com.inventory.dto.master.MasterDataDtos.CustomerResponse;
import com.inventory.entity.Customer;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.MasterDataMapper;
import com.inventory.repository.CustomerRepository;
import com.inventory.repository.SaleRepository;
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

    public CustomerService(CustomerRepository customers, SaleRepository sales) {
        this.customers = customers;
        this.sales = sales;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> search(String search, Boolean active, Pageable pageable) {
        return PageResponse.of(customers.search(searchTerm(search), active, pageable), MasterDataMapper::toResponse);
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
        if (sales.existsByCustomerId(id)) {
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

    private Customer load(Long id) {
        return customers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }
}
