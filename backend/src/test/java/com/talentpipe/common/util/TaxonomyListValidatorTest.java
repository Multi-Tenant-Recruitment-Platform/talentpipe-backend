package com.talentpipe.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TaxonomyListValidator}.
 * Pure Java — no Spring context required.
 */
class TaxonomyListValidatorTest {

    private TaxonomyListValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TaxonomyListValidator();
        // Simulate @ValidTaxonomyList(maxSize = 50, maxEntryLength = 100)
        ValidTaxonomyList annotation = createAnnotation(50, 100);
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

    // ---------------------------------------------------------------- within limits

    @Test
    void singleShortEntry_isValid() {
        assertThat(validator.isValid(List.of("Engineering"), null)).isTrue();
    }

    @Test
    void exactlyMaxEntries_isValid() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            list.add("Entry-" + i);
        }
        assertThat(validator.isValid(list, null)).isTrue();
    }

    @Test
    void entryExactlyMaxLength_isValid() {
        String maxLengthEntry = "A".repeat(100);
        assertThat(validator.isValid(List.of(maxLengthEntry), null)).isTrue();
    }

    // ---------------------------------------------------------------- exceeds limits

    @Test
    void listWithFiftyOneEntries_isInvalid() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            list.add("Entry-" + i);
        }
        assertThat(validator.isValid(list, null)).isFalse();
    }

    @Test
    void entryExceedingMaxLength_isInvalid() {
        String tooLong = "A".repeat(101);
        assertThat(validator.isValid(List.of("Valid", tooLong), null)).isFalse();
    }

    @Test
    void entryExactlyOneBeyondMaxLength_isInvalid() {
        String oneTooLong = "B".repeat(101);
        assertThat(validator.isValid(List.of(oneTooLong), null)).isFalse();
    }

    // ---------------------------------------------------------------- null / blank entries

    @Test
    void listWithNullEntries_nullIgnored_sizeCheckedOnAll() {
        List<String> withNulls = Arrays.asList("Engineering", null, "Product");
        assertThat(validator.isValid(withNulls, null)).isTrue();
    }

    @Test
    void listWithBlankEntries_blankCountsTowardSize() {
        // Blank entries are counted toward the size limit; they're filtered
        // by the normalizer before persistence, not here
        List<String> withBlanks = Arrays.asList("Engineering", "  ", "Product");
        assertThat(validator.isValid(withBlanks, null)).isTrue();
    }

    @Test
    void entryWhitespaceStrippedForLengthCheck() {
        // "  A  " trims to "A" → length 1, well within 100
        assertThat(validator.isValid(List.of("  A  "), null)).isTrue();
    }

    // ---------------------------------------------------------------- custom limits

    @Test
    void customMaxSize_enforcedCorrectly() {
        TaxonomyListValidator strictValidator = new TaxonomyListValidator();
        strictValidator.initialize(createAnnotation(3, 100));

        List<String> four = List.of("A", "B", "C", "D");
        assertThat(strictValidator.isValid(four, null)).isFalse();

        List<String> three = List.of("A", "B", "C");
        assertThat(strictValidator.isValid(three, null)).isTrue();
    }

    @Test
    void customMaxEntryLength_enforcedCorrectly() {
        TaxonomyListValidator strictValidator = new TaxonomyListValidator();
        strictValidator.initialize(createAnnotation(50, 10));

        assertThat(strictValidator.isValid(List.of("12345678901"), null)).isFalse(); // 11 chars
        assertThat(strictValidator.isValid(List.of("1234567890"), null)).isTrue();   // 10 chars
    }

    // ---------------------------------------------------------------- helper

    private static ValidTaxonomyList createAnnotation(int maxSize, int maxEntryLength) {
        return new ValidTaxonomyList() {
            @Override
            public int maxSize() {
                return maxSize;
            }

            @Override
            public int maxEntryLength() {
                return maxEntryLength;
            }

            @Override
            public String message() {
                return "list exceeds limits";
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
                return ValidTaxonomyList.class;
            }
        };
    }
}
