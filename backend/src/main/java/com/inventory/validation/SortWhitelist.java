package com.inventory.validation;

import com.inventory.exception.BusinessException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/** Rejects sort parameters that are not explicitly allowed for an endpoint. */
public final class SortWhitelist {

    private final Set<String> allowed;

    private SortWhitelist(Set<String> allowed) {
        this.allowed = allowed;
    }

    public static SortWhitelist of(String... properties) {
        return new SortWhitelist(Set.of(properties));
    }

    public Pageable check(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new BusinessException("Cannot sort by '" + order.getProperty() + "'");
            }
        }
        return pageable;
    }
}
