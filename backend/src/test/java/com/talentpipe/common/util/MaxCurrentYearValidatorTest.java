package com.talentpipe.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Year;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link MaxCurrentYearValidator}. */
class MaxCurrentYearValidatorTest {

    private final MaxCurrentYearValidator validator = new MaxCurrentYearValidator();

    @Test
    void null_isValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void currentYear_isValid() {
        assertThat(validator.isValid(Year.now().getValue(), null)).isTrue();
    }

    @Test
    void pastYear_isValid() {
        assertThat(validator.isValid(2000, null)).isTrue();
    }

    @Test
    void year1800_isValid() {
        assertThat(validator.isValid(1800, null)).isTrue();
    }

    @Test
    void futureYear_isInvalid() {
        assertThat(validator.isValid(Year.now().getValue() + 1, null)).isFalse();
    }

    @Test
    void farFutureYear_isInvalid() {
        assertThat(validator.isValid(9999, null)).isFalse();
    }
}
