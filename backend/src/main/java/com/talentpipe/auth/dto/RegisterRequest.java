package com.talentpipe.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Company onboarding request (PB-001): creates a tenant and its first
 * COMPANY_ADMIN user in one atomic operation.
 */
public record RegisterRequest(

        @NotBlank(message = "companyName is required")
        @Size(max = 255)
        String companyName,

        @NotBlank(message = "subdomain is required")
        @Size(min = 2, max = 100)
        @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$",
                message = "must contain only lowercase letters, digits and inner hyphens")
        String subdomain,

        @NotNull(message = "admin is required")
        @Valid
        AdminUser admin
) {

    /** The first (COMPANY_ADMIN) user of the new tenant. */
    public record AdminUser(

            @NotBlank(message = "firstName is required")
            @Size(max = 100)
            String firstName,

            @NotBlank(message = "lastName is required")
            @Size(max = 100)
            String lastName,

            @NotBlank(message = "email is required")
            @Email
            @Size(max = 255)
            String email,

            // Upper bound: bcrypt only hashes the first 72 bytes of input.
            @NotBlank(message = "password is required")
            @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
            String password
    ) {
    }
}
