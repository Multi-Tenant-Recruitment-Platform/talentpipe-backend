package com.talentpipe.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request payload for public global forgot password requests (no tenant subdomain). */
public record ForgotPasswordRequest(
        @NotBlank(message = "Enter your email address.")
        @Email(message = "Enter a valid email address, like you@company.com.")
        String email
) {
}
