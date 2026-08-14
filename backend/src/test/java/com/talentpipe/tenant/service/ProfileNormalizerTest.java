package com.talentpipe.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link ProfileNormalizer}.
 * No Spring context required — this is a pure-logic component.
 */
class ProfileNormalizerTest {

    private final ProfileNormalizer normalizer = new ProfileNormalizer();

    // ---------------------------------------------------------------- normalizeLine

    @Test
    void normalizeLine_null_returnsNull() {
        assertThat(normalizer.normalizeLine(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    void normalizeLine_blank_returnsNull(String blank) {
        assertThat(normalizer.normalizeLine(blank)).isNull();
    }

    @Test
    void normalizeLine_trimsLeadingAndTrailingWhitespace() {
        assertThat(normalizer.normalizeLine("  Acme Inc  ")).isEqualTo("Acme Inc");
    }

    @Test
    void normalizeLine_collapsesInternalWhitespace() {
        assertThat(normalizer.normalizeLine("Acme  Corp   Ltd")).isEqualTo("Acme Corp Ltd");
    }

    @Test
    void normalizeLine_preservesSingleInternalSpaces() {
        assertThat(normalizer.normalizeLine("Acme Corp")).isEqualTo("Acme Corp");
    }

    // -------------------------------------------------------------- normalizeMultiline

    @Test
    void normalizeMultiline_null_returnsNull() {
        assertThat(normalizer.normalizeMultiline(null)).isNull();
    }

    @Test
    void normalizeMultiline_blank_returnsNull() {
        assertThat(normalizer.normalizeMultiline("   ")).isNull();
    }

    @Test
    void normalizeMultiline_trimsButPreservesInternalNewlines() {
        String input = "  Line one\nLine two\n  Line three  ";
        String result = normalizer.normalizeMultiline(input);
        assertThat(result).isEqualTo("Line one\nLine two\n  Line three");
    }

    @Test
    void normalizeMultiline_preservesParagraphSpacing() {
        String input = "Para one.\n\nPara two.";
        assertThat(normalizer.normalizeMultiline(input)).isEqualTo("Para one.\n\nPara two.");
    }

    // ---------------------------------------------------------------- normalizeUrl

    @Test
    void normalizeUrl_null_returnsNull() {
        assertThat(normalizer.normalizeUrl(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void normalizeUrl_blank_returnsNull(String blank) {
        assertThat(normalizer.normalizeUrl(blank)).isNull();
    }

    @Test
    void normalizeUrl_prependsHttpsWhenNoScheme() {
        assertThat(normalizer.normalizeUrl("talentpipe.io")).isEqualTo("https://talentpipe.io");
    }

    @Test
    void normalizeUrl_doesNotDoublePrependHttps() {
        assertThat(normalizer.normalizeUrl("https://talentpipe.io")).isEqualTo("https://talentpipe.io");
    }

    @Test
    void normalizeUrl_preservesHttpScheme() {
        assertThat(normalizer.normalizeUrl("http://talentpipe.io")).isEqualTo("http://talentpipe.io");
    }

    @Test
    void normalizeUrl_trimsWhitespace() {
        assertThat(normalizer.normalizeUrl("  https://talentpipe.io  ")).isEqualTo("https://talentpipe.io");
    }

    @Test
    void normalizeUrl_prependsHttpsToLinkedIn() {
        assertThat(normalizer.normalizeUrl("linkedin.com/company/acme"))
                .isEqualTo("https://linkedin.com/company/acme");
    }

    // ---------------------------------------------------------------- normalizeList

    @Test
    void normalizeList_null_returnsEmptyList() {
        assertThat(normalizer.normalizeList(null)).isEmpty();
    }

    @Test
    void normalizeList_empty_returnsEmptyList() {
        assertThat(normalizer.normalizeList(Collections.emptyList())).isEmpty();
    }

    @Test
    void normalizeList_removesBlankEntries() {
        List<String> input = Arrays.asList("Java", "", "  ", null, "Python");
        assertThat(normalizer.normalizeList(input)).containsExactly("Java", "Python");
    }

    @Test
    void normalizeList_trimsEntries() {
        List<String> input = Arrays.asList("  Java  ", " Python ");
        assertThat(normalizer.normalizeList(input)).containsExactly("Java", "Python");
    }

    @Test
    void normalizeList_deduplicatesCaseInsensitively() {
        List<String> input = Arrays.asList("Java", "java", "JAVA");
        assertThat(normalizer.normalizeList(input)).containsExactly("Java"); // first occurrence kept
    }

    @Test
    void normalizeList_preservesFirstOccurrenceCasing() {
        List<String> input = Arrays.asList("Remote", "remote", "REMOTE");
        List<String> result = normalizer.normalizeList(input);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo("Remote");
    }

    @Test
    void normalizeList_preservesOrderOfFirstOccurrences() {
        List<String> input = Arrays.asList("Python", "Java", "python", "Go");
        assertThat(normalizer.normalizeList(input)).containsExactly("Python", "Java", "Go");
    }

    @Test
    void normalizeList_returnsUnmodifiableList() {
        List<String> result = normalizer.normalizeList(Arrays.asList("Java"));
        assertThat(result).isUnmodifiable();
    }
}
