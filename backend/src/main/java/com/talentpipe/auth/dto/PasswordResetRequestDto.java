package com.talentpipe.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for initiating a password reset (PB-008).
 *
 * <p>The subdomain is needed because users are scoped per tenant — the same
 * email may belong to accounts in different companies. It plays the same role
 * here as the {@code X-Tenant-Subdomain} header does at login.</p>
 */
public record PasswordResetRequestDto(

        @NotBlank(message = "email is required")
        @Email(message = "must be a valid email address")
        @Size(max = 255)
        String email,

        @NotBlank(message = "subdomain is required")
        @Size(min = 2, max = 100)
        String subdomain
) {
}
