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

        @NotBlank(message = "email is required")
        @Email
        String email,

        @NotBlank(message = "password is required")
        String password
) {
}
