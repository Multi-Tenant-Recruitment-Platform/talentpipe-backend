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

    @Test
    void punctuationAndSpacingVariantsOfAnOption_areValid() {
        // "On-site" as a slug, as two words, and as one — all the same option
        assertThat(validator.isValid(List.of("on_site"), null)).isTrue();
        assertThat(validator.isValid(List.of("On site"), null)).isTrue();
        assertThat(validator.isValid(List.of("onsite"), null)).isTrue();
    }

    @Test
    void surroundingWhitespace_isValid() {
        assertThat(validator.isValid(List.of("  Remote  "), null)).isTrue();
    }

    @Test
    void labelAndSlugSpellingsOfAMultiWordOption_areValid() {
        AllowedValuesValidator benefits = new AllowedValuesValidator();
        benefits.initialize(createAnnotation(new String[] {
                "Remote / hybrid work", "Training & development"}));

        assertThat(benefits.isValid(List.of("Remote / hybrid work"), null)).isTrue();
        assertThat(benefits.isValid(List.of("remote_hybrid_work"), null)).isTrue();
        assertThat(benefits.isValid(List.of("Remote/Hybrid Work"), null)).isTrue();
        // "&" written out is the same option, not a different one
        assertThat(benefits.isValid(List.of("Training and development"), null)).isTrue();
        assertThat(benefits.isValid(List.of("training-and-development"), null)).isTrue();
        // ...but an option that is genuinely absent is still rejected
        assertThat(benefits.isValid(List.of("Free coffee"), null)).isFalse();
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
    void punctuationOnlyEntry_isInvalid() {
        // folds to an empty key, which must not be treated as a match
        assertThat(validator.isValid(List.of("///"), null)).isFalse();
    }

    // ---------------------------------------------------------------- blank entries

    @Test
    void emptyStringEntry_isValid() {
        // blanks are dropped by ProfileNormalizer, not unrecognised options
        assertThat(validator.isValid(List.of(""), null)).isTrue();
    }

    @Test
    void blankStringEntry_isValid() {
        assertThat(validator.isValid(List.of("   "), null)).isTrue();
    }

    @Test
    void blankEntryAlongsideAllowedValues_isValid() {
        assertThat(validator.isValid(Arrays.asList("Remote", "", "Hybrid"), null)).isTrue();
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
