package com.talentpipe.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for email verification (PB-001). */
public record VerifyEmailRequest(

        @NotBlank(message = "token is required")
        String token
) {
}
