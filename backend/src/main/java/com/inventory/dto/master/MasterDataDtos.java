package com.inventory.dto.master;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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

    // --- Customers ---

    public record CustomerRequest(
            @NotBlank(message = "Name is required") @Size(max = 150) String name,
            @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
            @Email(message = "Email address is not valid") @Size(max = 150) String email,
            @Size(max = 500) String address,
            Boolean active,
            Long version) {
    }

    public record CustomerResponse(
            Long id, String name, String phone, String email, String address,
            boolean active, long version, long orderCount) {
    }
}
