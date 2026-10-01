package com.inventory.dto.master;

import com.inventory.entity.ProductType;
import com.inventory.validation.ValidIsbn;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Requests carry {@code version} on update: a stale version means someone else saved first,
 * and the update is rejected with 409 instead of silently overwriting their change.
 */
public final class MasterDataDtos {

    private MasterDataDtos() {
    }

    static final String PHONE_REGEX = "^[0-9+()\\- ]{0,30}$";
    static final String PHONE_MESSAGE = "Phone number is not valid";

    public record Ref(Long id, String name) {
    }

    // --- Publishers and suppliers (same shape) ---

    public record PartyRequest(
            @NotBlank(message = "Name is required") @Size(max = 150) String name,
            @Size(max = 100) String contactPerson,
            @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
            @Email(message = "Email address is not valid") @Size(max = 150) String email,
            @Size(max = 500) String address,
            Boolean active,
            Long version) {
    }

    public record PartyResponse(
            Long id, String name, String contactPerson, String phone, String email, String address,
            boolean active, long version, Instant createdAt, Instant updatedAt) {
    }

    // --- Representatives ---

    public record RepresentativeRequest(
            @NotBlank(message = "Name is required") @Size(max = 150) String name,
            @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
            @Email(message = "Email address is not valid") @Size(max = 150) String email,
            @Size(max = 500) String address,
            @Size(max = 150) String area,
            Boolean active,
            Long version) {
    }

    public record RepresentativeResponse(
            Long id, String name, String phone, String email, String address, String area,
            boolean active, boolean builtIn, long customerCount, long version, Instant createdAt, Instant updatedAt) {
    }

    /** Representative shown on customers and invoices. */
    public record RepresentativeRef(Long id, String name, String area, String phone) {
    }

    public record MoveCustomersRequest(@NotNull(message = "Choose the representative to move customers to")
                                       Long targetRepresentativeId) {
    }

    public record MoveCustomersResponse(int moved, RepresentativeRef from, RepresentativeRef to) {
    }

    // --- Customers ---

    /** {@code representativeId} is required for every customer except the walk-in customer. */
    public record CustomerRequest(
            @NotBlank(message = "Name is required") @Size(max = 150) String name,
            @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
            @Email(message = "Email address is not valid") @Size(max = 150) String email,
            @Size(max = 500) String address,
            Long representativeId,
            Boolean active,
            Long version) {
    }

    public record CustomerResponse(
            Long id, String name, String phone, String email, String address,
            RepresentativeRef representative, boolean active, boolean walkIn, long version, Instant createdAt, Instant updatedAt) {
    }

    // --- Categories ---

    public record CategoryRequest(
            @NotBlank(message = "Name is required") @Size(max = 100) String name,
            @Size(max = 500) String description,
            Boolean active,
            Long version) {
    }

    public record CategoryResponse(
            Long id, String name, String description, boolean active, long version, Instant createdAt, Instant updatedAt) {
    }

    // --- Products ---

    public record ProductRequest(
            @ValidIsbn String isbn,
            @NotBlank(message = "Title is required") @Size(max = 300) String title,
            @Size(max = 200) String author,
            Long publisherId,
            Long categoryId,
            @Size(max = 50) String edition,
            @Min(value = 1450, message = "Publication year is not valid")
            @Max(value = 2100, message = "Publication year is not valid") Integer publicationYear,
            @NotNull(message = "Purchase price is required")
            @DecimalMin(value = "0.00", message = "Purchase price cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "Purchase price must have at most 2 decimals")
            BigDecimal purchasePrice,
            @NotNull(message = "Selling price is required")
            @DecimalMin(value = "0.01", message = "Selling price must be greater than zero")
            @Digits(integer = 10, fraction = 2, message = "Selling price must have at most 2 decimals")
            BigDecimal sellingPrice,
            Boolean active,
            Long version) {
    }

    public record ProductResponse(
            Long id, ProductType productType, String isbn, String title, String author,
            Ref publisher, Ref category, String edition, Integer publicationYear,
            BigDecimal purchasePrice, BigDecimal sellingPrice,
            boolean active, long version, Instant createdAt, Instant updatedAt) {
    }
}
