package com.talentpipe.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request payload for public global forgot password requests (no tenant subdomain). */
public record ForgotPasswordRequest(
        @NotBlank(message = "email is required")
        @Email(message = "must be a valid email address")
        String email
) {
}
