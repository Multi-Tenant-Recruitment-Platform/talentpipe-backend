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

    // ---------------------------------------------------------------- scheme whitelist

    /**
     * These fields are later rendered as {@code href} attributes on the
     * public company page: any scheme besides http/https is a stored-XSS or
     * local-file-read vector, not a "valid but unusual" URL.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "javascript://evil.com/%0aalert(1)",
            "JAVASCRIPT://evil.com/%0aalert(1)",   // scheme match is case-insensitive
            "vbscript://evil.com/x",
            "data:text/html;base64,PHNjcmlwdD4=",
            "file:///etc/passwd",
            "ftp://files.example.com/x"
    })
    void nonHttpSchemes_areRejected(String url) {
        assertThat(validator.isValid(url, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTPS://Example.com", "HTTP://example.com"})
    void httpSchemes_areAcceptedRegardlessOfCase(String url) {
        assertThat(validator.isValid(url, null)).isTrue();
    }
}
