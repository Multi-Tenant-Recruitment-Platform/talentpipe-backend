package com.talentpipe.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for confirming a password reset (PB-008). */
public record PasswordResetConfirmRequest(

        @NotBlank(message = "This reset link is incomplete. Open the link from your email again.")
        String token,

        // Same bounds as registration: bcrypt only hashes the first 72 bytes.
        @NotBlank(message = "Choose a new password.")
        @Size(min = 8, max = 72, message = "Use a password of 8 to 72 characters.")
        String newPassword
) {
}
