package com.inventory.dto.measurement;

import com.inventory.dto.garment.GarmentDtos.GarmentRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Measurement records are created and read only: there is no update or delete. Values are inches. */
public final class MeasurementDtos {

    private MeasurementDtos() {
    }

    public record MeasurementValueRequest(
            @NotNull(message = "Measurement point is required") Long pointId,
            @NotNull(message = "Measurement value is required")
            @DecimalMin(value = "0.01", message = "Measurements must be greater than zero")
            @Digits(integer = 4, fraction = 2, message = "Measurements must have at most 2 decimals")
            BigDecimal value) {
    }

    /** {@code takenOn} defaults to today (shop time); it cannot be in the future. */
    public record MeasurementRequest(
            @NotNull(message = "Dress type is required") Long garmentTypeId,
            LocalDate takenOn,
            @Size(max = 500) String notes,
            @Valid @NotNull(message = "Enter at least one measurement")
            @Size(min = 1, message = "Enter at least one measurement") List<MeasurementValueRequest> values) {
    }

    public record MeasurementValueResponse(Long pointId, String label, BigDecimal value, int sortOrder) {
    }

    public record MeasurementResponse(
            Long id, Long customerId, String customerName, GarmentRef garmentType, LocalDate takenOn,
            String notes, Instant createdAt, String createdBy, List<MeasurementValueResponse> values) {
    }
}
