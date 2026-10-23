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

        @NotBlank(message = "Enter your email address.")
        @Email(message = "Enter a valid email address, like you@company.com.")
        @Size(max = 255, message = "Keep the email address to 255 characters or fewer.")
        String email
) {
}
