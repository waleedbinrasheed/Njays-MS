package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.master.MasterDataDtos.CustomerRequest;
import com.inventory.dto.master.MasterDataDtos.CustomerResponse;
import com.inventory.entity.Customer;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.MasterDataMapper;
import com.inventory.repository.CustomerOrderRepository;
import com.inventory.repository.CustomerRepository;
import com.inventory.repository.MeasurementRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.inventory.service.MasterDataSupport.checkVersion;
import static com.inventory.service.MasterDataSupport.deleteIfUnused;
import static com.inventory.service.MasterDataSupport.inUse;
import static com.inventory.service.MasterDataSupport.searchTerm;
import static com.inventory.service.MasterDataSupport.trimToNull;

/**
 * Customers are people who order garments. Walk-in rows from the book shop are invisible here.
 * Two active customers may not share both name and phone; a customer with orders or measurements
 * cannot be deleted (deactivate instead).
 */
@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final CustomerOrderRepository orders;
    private final MeasurementRecordRepository measurements;

    public CustomerService(CustomerRepository customers, CustomerOrderRepository orders,
                           MeasurementRecordRepository measurements) {
        this.customers = customers;
        this.orders = orders;
        this.measurements = measurements;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> search(String search, Boolean active, Pageable pageable) {
        Page<Customer> page = customers.search(searchTerm(search), active, pageable);
        Map<Long, Long> counts = orderCounts(page.getContent());
        return PageResponse.of(page, c -> MasterDataMapper.toResponse(c, counts.getOrDefault(c.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        Customer customer = load(id);
        return MasterDataMapper.toResponse(customer, orders.countByCustomerId(id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        String name = request.name().trim();
        String phone = trimToNull(request.phone());
        boolean active = request.active() == null || request.active();
        if (active) {
            checkNoDuplicate(name, phone, null);
        }
        Customer customer = new Customer();
        customer.setName(name);
        customer.setPhone(phone);
        customer.setEmail(trimToNull(request.email()));
        customer.setAddress(trimToNull(request.address()));
        customer.setActive(active);
        return MasterDataMapper.toResponse(customers.save(customer), 0);
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = load(id);
        checkVersion(customer, request.version());
        String name = request.name().trim();
        String phone = trimToNull(request.phone());
        boolean active = request.active() == null || request.active();
        if (active) {
            checkNoDuplicate(name, phone, id);
        }
        customer.setName(name);
        customer.setPhone(phone);
        customer.setEmail(trimToNull(request.email()));
        customer.setAddress(trimToNull(request.address()));
        customer.setActive(active);
        Customer saved = customers.saveAndFlush(customer);
        return MasterDataMapper.toResponse(saved, orders.countByCustomerId(id));
    }

    @Transactional
    public void delete(Long id) {
        Customer customer = load(id);
        String label = "Customer '" + customer.getName() + "'";
        if (orders.existsByCustomerId(id) || measurements.existsByCustomerId(id)) {
            throw inUse(label);
        }
        deleteIfUnused(customers, customer, label);
    }

    /** Active customer with the same name and phone (ignoring case) is refused. */
    private void checkNoDuplicate(String name, String phone, Long excludeId) {
        long same = customers.countActiveDuplicates(name.toLowerCase(Locale.ROOT),
                phone == null ? "" : phone, excludeId);
        if (same > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "An active customer named '" + name
                    + "' with this phone number already exists");
        }
    }

    private Map<Long, Long> orderCounts(Collection<Customer> page) {
        if (page.isEmpty()) return Map.of();
        List<Long> ids = page.stream().map(Customer::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : orders.countPerCustomer(ids)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private Customer load(Long id) {
        return customers.findById(id)
                .filter(c -> !c.isWalkIn())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }
}
