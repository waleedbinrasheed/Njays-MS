package com.inventory.validation;

import com.inventory.exception.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Rejects sort parameters that are not explicitly allowed for an endpoint, and optionally maps the
 * public sort names to entity paths (e.g. "title" -> "product.title").
 */
public final class SortWhitelist {

    private final Map<String, String> allowed;

    private SortWhitelist(Map<String, String> allowed) {
        this.allowed = allowed;
    }

    public static SortWhitelist of(String... properties) {
        return new SortWhitelist(Set.of(properties).stream().collect(Collectors.toMap(Function.identity(), Function.identity())));
    }

    /** Public name -> entity property path. */
    public static SortWhitelist aliased(Map<String, String> aliases) {
        return new SortWhitelist(Map.copyOf(aliases));
    }

    public Pageable check(Pageable pageable) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            String path = allowed.get(order.getProperty());
            if (path == null) {
                throw new BusinessException("Cannot sort by '" + order.getProperty() + "'");
            }
            orders.add(order.withProperty(path));
        }
        // Stable paging: rows with equal sort values always come back in the same order.
        if (orders.stream().noneMatch(o -> o.getProperty().equals("id"))) {
            Sort.Direction direction = orders.isEmpty() ? Sort.Direction.ASC : orders.getFirst().getDirection();
            orders.add(new Sort.Order(direction, "id"));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }
}
