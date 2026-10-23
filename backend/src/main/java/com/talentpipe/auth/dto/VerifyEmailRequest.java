package com.talentpipe.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for email verification (PB-001). */
public record VerifyEmailRequest(

        @NotBlank(message = "This verification link is incomplete. Open the link from your email again.")
        String token
) {
}
