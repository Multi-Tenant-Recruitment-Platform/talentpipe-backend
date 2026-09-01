package com.talentpipe.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;

/**
 * Unit tests for {@link AllowedValuesValidator}.
 * Pure Java — no Spring context required.
 */
class AllowedValuesValidatorTest {

    private static final String[] ALLOWED = {"Remote", "Hybrid", "On-site"};

    private AllowedValuesValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AllowedValuesValidator();
        // Simulate @AllowedValues(value = {"Remote", "Hybrid", "On-site"})
        AllowedValues annotation = createAnnotation(ALLOWED);
        validator.initialize(annotation);
    }

    // ---------------------------------------------------------------- null / empty

    @Test
    void nullList_isValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void emptyList_isValid() {
        assertThat(validator.isValid(Collections.emptyList(), null)).isTrue();
    }

    // ---------------------------------------------------------------- valid values

    @Test
    void singleAllowedValue_isValid() {
        assertThat(validator.isValid(List.of("Remote"), null)).isTrue();
    }

    @Test
    void allAllowedValues_isValid() {
        assertThat(validator.isValid(Arrays.asList("Remote", "Hybrid", "On-site"), null)).isTrue();
    }

    @Test
    void matchingIsCaseInsensitive_isValid() {
        assertThat(validator.isValid(List.of("remote"), null)).isTrue();
        assertThat(validator.isValid(List.of("HYBRID"), null)).isTrue();
        assertThat(validator.isValid(List.of("ON-SITE"), null)).isTrue();
    }

    @Test
    void mixedCasing_isValid() {
        assertThat(validator.isValid(Arrays.asList("Remote", "hybrid", "ON-SITE"), null)).isTrue();
    }

    // ---------------------------------------------------------------- invalid values

    @Test
    void unknownValue_isInvalid() {
        assertThat(validator.isValid(List.of("Freelance"), null)).isFalse();
    }

    @Test
    void mixedValidAndInvalidValues_isInvalid() {
        assertThat(validator.isValid(Arrays.asList("Remote", "Unknown"), null)).isFalse();
    }

    @Test
    void emptyStringEntry_isInvalid() {
        assertThat(validator.isValid(List.of(""), null)).isFalse();
    }

    @Test
    void blankStringEntry_isInvalid() {
        assertThat(validator.isValid(List.of("   "), null)).isFalse();
    }

    // ---------------------------------------------------------------- null entries in list

    @Test
    void listWithNullEntry_nullIsSkipped_validValuesPass() {
        // null entries are filtered; remaining values must all be in the whitelist
        List<String> mixed = Arrays.asList("Remote", null, "Hybrid");
        assertThat(validator.isValid(mixed, null)).isTrue();
    }

    @Test
    void listWithOnlyNullEntries_isValid() {
        List<String> nullOnly = Arrays.asList(null, null);
        assertThat(validator.isValid(nullOnly, null)).isTrue();
    }

    // ---------------------------------------------------------------- helper

    private static AllowedValues createAnnotation(String[] allowedValues) {
        return new AllowedValues() {
            @Override
            public String[] value() {
                return allowedValues;
            }

            @Override
            public String message() {
                return "contains invalid value(s)";
            }

            @Override
            public Class<?>[] groups() {
                return new Class[0];
            }

            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return AllowedValues.class;
            }
        };
    }
}
