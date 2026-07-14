package com.talentpipe.auth.dto;

/**
 * Successful authentication result (login and refresh).
 *
 * @param accessToken  short-lived JWT for the Authorization header
 * @param refreshToken long-lived rotating JWT; presented only to /auth/refresh
 *                     and /auth/logout
 * @param expiresIn    access-token lifetime in seconds
 * @param user         the authenticated user's profile
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserResponse user
) {
}
