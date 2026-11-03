package com.inventory.dto.fabric;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class FabricDtos {

    private FabricDtos() {
    }

    /** Prices are per metre. {@code reorderLevel} defaults to 0 and is in metres. */
    public record FabricRequest(
            @NotBlank(message = "Fabric code is required") @Size(max = 40) String code,
            @NotBlank(message = "Fabric name is required") @Size(max = 300) String name,
            @Size(max = 100) String material,
            @Size(max = 60) String colour,
            @Size(max = 100) String pattern,
            @Size(max = 30) String width,
            @NotNull(message = "Cost per metre is required")
            @DecimalMin(value = "0.00", message = "Cost per metre cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "Cost per metre must have at most 2 decimals")
            BigDecimal costPerMetre,
            @NotNull(message = "Price per metre is required")
            @DecimalMin(value = "0.01", message = "Price per metre must be greater than zero")
            @Digits(integer = 10, fraction = 2, message = "Price per metre must have at most 2 decimals")
            BigDecimal pricePerMetre,
            @DecimalMin(value = "0", message = "Reorder level cannot be negative")
            @Digits(integer = 9, fraction = 3, message = "Reorder level must have at most 3 decimals")
            BigDecimal reorderLevel,
            Boolean active,
            Long version) {
    }

    public record FabricResponse(
            Long id, String code, String name, String material, String colour, String pattern, String width,
            BigDecimal costPerMetre, BigDecimal pricePerMetre, boolean active, long version,
            BigDecimal quantityOnHand, BigDecimal reorderLevel, boolean lowStock) {
    }
}
