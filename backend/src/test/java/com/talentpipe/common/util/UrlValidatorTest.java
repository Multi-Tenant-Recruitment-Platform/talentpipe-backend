package com.talentpipe.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit tests for {@link UrlValidator}. */
class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator();

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
            "https://talentpipe.io",
            "http://localhost:8080",
            "https://www.example.com/path?q=1",
            "talentpipe.io",                   // no scheme — normalizer prepends https://
            "linkedin.com/company/acme"
    })
    void validUrls_areAccepted(String url) {
        assertThat(validator.isValid(url, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not a url",
            "://missing-scheme",
            "ftp://"                           // no host
    })
    void invalidUrls_areRejected(String url) {
        assertThat(validator.isValid(url, null)).isFalse();
    }
}
