package com.talentpipe.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /auth/refresh and POST /auth/logout. */
public record RefreshTokenRequest(

        @NotBlank(message = "refreshToken is required")
        String refreshToken
) {
}
