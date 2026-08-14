package com.talentpipe.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit tests for {@link PhoneValidator}. */
class PhoneValidatorTest {

    private final PhoneValidator validator = new PhoneValidator();

    @Test
    void null_isValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blank_isValid(String blank) {
        assertThat(validator.isValid(blank, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+1-800-555-0100",
            "0044 20 7946 0958",
            "+919876543210",
            "7654321",          // 7 digits exactly (minimum)
            "123456789012345"   // 15 digits exactly (maximum)
    })
    void validPhones_areAccepted(String phone) {
        assertThat(validator.isValid(phone, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12345",            // only 5 digits — too short
            "1234567890123456", // 16 digits — too long
            "no-digits-here",
            "++"
    })
    void invalidPhones_areRejected(String phone) {
        assertThat(validator.isValid(phone, null)).isFalse();
    }
}
