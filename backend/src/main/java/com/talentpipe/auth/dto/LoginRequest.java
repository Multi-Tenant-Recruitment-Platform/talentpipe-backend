package com.talentpipe.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Login credentials (PB-007). The tenant is deliberately NOT part of the
 * body — it is resolved from the {@code X-Tenant-Subdomain} header this
 * sprint (Host-header subdomain in production; see docs/DECISIONS.md).
 * Tenant identity never comes from a request body.
 */
public record LoginRequest(

        @NotBlank(message = "Enter your email address.")
        @Email(message = "Enter a valid email address, like you@company.com.")
        String email,

        @NotBlank(message = "Enter your password.")
        String password
) {
}
