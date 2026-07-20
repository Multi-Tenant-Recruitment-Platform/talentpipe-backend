package com.talentpipe.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Accepts an invitation by setting the account's first password (PB-003 / PB-004).
 *
 * <p>Only the token and the password: the tenant and role come from the INVITED
 * user row the token points at, never from the request.</p>
 */
public record AcceptInviteRequest(

        @NotBlank(message = "token is required")
        String token,

        // Same bounds as registration: bcrypt only hashes the first 72 bytes.
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        String password
) {
}
