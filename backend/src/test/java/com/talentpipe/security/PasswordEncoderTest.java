package com.talentpipe.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Pins the platform password-hashing scheme: bcrypt with cost factor 12
 * (architecture doc requirement). If someone changes the encoder or its
 * strength, this test fails loudly.
 */
class PasswordEncoderTest {

    private final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

    @Test
    void encodesWithBcryptCostFactor12() {
        String hash = encoder.encode("correct horse battery staple");

        // bcrypt format: $2<version>$<cost>$<22-char salt><31-char hash>
        assertThat(hash).matches("^\\$2[aby]\\$12\\$.{53}$");
    }

    @Test
    void matchesRoundTrip_andRejectsWrongPassword() {
        String hash = encoder.encode("s3cret-password");

        assertThat(encoder.matches("s3cret-password", hash)).isTrue();
        assertThat(encoder.matches("wrong-password", hash)).isFalse();
    }
}
