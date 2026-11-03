package com.inventory.dto.garment;

import com.inventory.entity.GarmentShape;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
            @NotNull(message = "Shape is required") GarmentShape shape,
            Boolean active,
            @Valid List<PointRequest> points,
            Long version) {
    }

    public record PointResponse(Long id, String label, int sortOrder, boolean active) {
    }

    public record GarmentTypeResponse(
            Long id, String name, String description, GarmentShape shape, boolean active, long version,
            List<PointResponse> points, boolean inUse) {
    }

    public record GarmentRef(Long id, String name, GarmentShape shape) {
    }
}
