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

        @NotBlank(message = "Enter your company name.")
        @Size(max = 255, message = "Keep the company name to 255 characters or fewer.")
        String companyName,

        @Size(max = 100, message = "Keep the workspace address to 100 characters or fewer.")
        String subdomain,

        @NotNull(message = "Enter the administrator's details.")
        @Valid
        AdminUser admin
) {

    /** The first (COMPANY_ADMIN) user of the new tenant. */
    public record AdminUser(

            @NotBlank(message = "Enter your first name.")
            @Size(max = 100, message = "Keep your first name to 100 characters or fewer.")
            String firstName,

            @NotBlank(message = "Enter your last name.")
            @Size(max = 100, message = "Keep your last name to 100 characters or fewer.")
            String lastName,

            @NotBlank(message = "Enter your work email address.")
            @Email(message = "Enter a valid email address, like you@company.com.")
            @Size(max = 255, message = "Keep the email address to 255 characters or fewer.")
            String email,

            // Upper bound: bcrypt only hashes the first 72 bytes of input.
            @NotBlank(message = "Choose a password.")
            @Size(min = 8, max = 72, message = "Use a password of 8 to 72 characters.")
            String password
    ) {
    }
}
