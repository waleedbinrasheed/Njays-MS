package com.inventory.dto.document;

import com.inventory.entity.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Requests shared by purchases and sales. */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record PaymentStatusRequest(@NotNull(message = "Payment status is required") PaymentStatus paymentStatus) {
    }

    public record CancelRequest(@NotBlank(message = "Give a reason for cancelling") @Size(max = 500) String reason) {
    }
}
