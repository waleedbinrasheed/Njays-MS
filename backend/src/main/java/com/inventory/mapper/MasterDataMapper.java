package com.inventory.mapper;

import com.inventory.dto.master.MasterDataDtos.CategoryResponse;
import com.inventory.dto.master.MasterDataDtos.CustomerResponse;
import com.inventory.dto.master.MasterDataDtos.PartyResponse;
import com.inventory.dto.master.MasterDataDtos.ProductResponse;
import com.inventory.dto.master.MasterDataDtos.Ref;
import com.inventory.entity.Category;
import com.inventory.entity.Customer;
import com.inventory.entity.Product;
import com.inventory.entity.Publisher;
import com.inventory.entity.Supplier;

public final class MasterDataMapper {

    private MasterDataMapper() {
    }

    public static PartyResponse toResponse(Publisher p) {
        return new PartyResponse(p.getId(), p.getName(), p.getContactPerson(), p.getPhone(), p.getEmail(),
                p.getAddress(), p.isActive(), p.getVersion(), p.getCreatedAt(), p.getUpdatedAt());
    }

    public static PartyResponse toResponse(Supplier s) {
        return new PartyResponse(s.getId(), s.getName(), s.getContactPerson(), s.getPhone(), s.getEmail(),
                s.getAddress(), s.isActive(), s.getVersion(), s.getCreatedAt(), s.getUpdatedAt());
    }

    public static CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getName(), c.getPhone(), c.getEmail(), c.getAddress(),
                c.isActive(), c.isWalkIn(), c.getVersion(), c.getCreatedAt(), c.getUpdatedAt());
    }

    public static CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive(), c.getVersion(),
                c.getCreatedAt(), c.getUpdatedAt());
    }

    public static ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getProductType(),
                p.getIsbn(),
                p.getTitle(),
                p.getAuthor(),
                p.getPublisher() == null ? null : new Ref(p.getPublisher().getId(), p.getPublisher().getName()),
                p.getCategory() == null ? null : new Ref(p.getCategory().getId(), p.getCategory().getName()),
                p.getEdition(),
                p.getPublicationYear() == null ? null : p.getPublicationYear().intValue(),
                p.getPurchasePrice(),
                p.getSellingPrice(),
                p.isActive(),
                p.getVersion(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
