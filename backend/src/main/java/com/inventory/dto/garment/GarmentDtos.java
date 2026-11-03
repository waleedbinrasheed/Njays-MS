package com.inventory.dto.garment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class GarmentDtos {

    private GarmentDtos() {
    }

    /**
     * A measurement point. {@code id} is null for a new point. Removed points are deleted when unused,
     * otherwise deactivated so old records keep their labels.
     */
    public record PointRequest(
            Long id,
            @NotBlank(message = "Point label is required") @Size(max = 80) String label,
            @NotNull(message = "Sort order is required") Integer sortOrder,
            Boolean active) {
    }

    public record GarmentTypeRequest(
            @NotBlank(message = "Name is required") @Size(max = 80) String name,
            @Size(max = 255) String description,
            @DecimalMin(value = "0", message = "Default price cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "Default price must have at most 2 decimals")
            BigDecimal defaultPrice,
            Boolean active,
            @Valid List<PointRequest> points,
            Long version) {
    }

    public record PointResponse(Long id, String label, int sortOrder, boolean active) {
    }

    public record GarmentTypeResponse(
            Long id, String name, String code, String description, BigDecimal defaultPrice, boolean active,
            long version, List<PointResponse> points, boolean inUse) {
    }

    /** {@code code} is null for a dress type the user added; only the built-in types have a picture. */
    public record GarmentRef(Long id, String name, String code) {
    }
}
