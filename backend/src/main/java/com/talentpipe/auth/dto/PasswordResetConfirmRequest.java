package com.talentpipe.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for confirming a password reset (PB-008). */
public record PasswordResetConfirmRequest(

        @NotBlank(message = "token is required")
        String token,

        // Same bounds as registration: bcrypt only hashes the first 72 bytes.
        @NotBlank(message = "newPassword is required")
        @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        String newPassword
) {
}
