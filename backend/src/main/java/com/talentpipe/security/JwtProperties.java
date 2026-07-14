package com.talentpipe.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration bound from {@code talentpipe.security.jwt.*}.
 *
 * @param secret          HS256 signing secret sourced from the {@code JWT_SECRET}
 *                        environment variable — no default on purpose, the app
 *                        fails fast without it. Minimum 32 bytes (256 bits),
 *                        enforced by {@link JwtTokenProvider}.
 * @param accessTokenTtl  access token lifetime (15 minutes per architecture doc)
 * @param refreshTokenTtl refresh token lifetime (7 days per architecture doc)
 */
@ConfigurationProperties(prefix = "talentpipe.security.jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
}
