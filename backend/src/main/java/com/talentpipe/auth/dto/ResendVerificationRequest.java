package com.talentpipe.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requests a fresh verification email (PB-001).
 *
 * <p>The tenant, when relevant, travels in the {@code X-Tenant-Subdomain}
 * header exactly as it does at login — never in this body. Without the header
 * the request is treated as a candidate's.</p>
 */
public record ResendVerificationRequest(

        @NotBlank(message = "email is required")
        @Email(message = "must be a valid email address")
        @Size(max = 255)
        String email
) {
}
