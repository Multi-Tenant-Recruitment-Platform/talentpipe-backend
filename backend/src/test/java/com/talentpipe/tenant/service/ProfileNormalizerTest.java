package com.talentpipe.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.common.util.HtmlSanitizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link ProfileNormalizer}.
 *
 * <p>Uses a real {@link HtmlSanitizer} (OWASP deny-all policy) so the full
 * normalization pipeline is exercised without mocking. No Spring context
 * required — both components are pure-logic POJOs.</p>
 */
class ProfileNormalizerTest {

    /** Real OWASP sanitizer — validates the full pipeline end-to-end. */
    private final HtmlSanitizer htmlSanitizer = new HtmlSanitizer();
    private final ProfileNormalizer normalizer = new ProfileNormalizer(htmlSanitizer);

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

    @Test
    void normalizeLine_stripsHtmlTags() {
        assertThat(normalizer.normalizeLine("<b>Acme Corp</b>")).isEqualTo("Acme Corp");
    }

    @Test
    void normalizeLine_stripsScriptXssPayload() {
        String xss = "<script>alert('xss')</script>Acme Corp";
        String result = normalizer.normalizeLine(xss);
        assertThat(result).doesNotContain("<script>");
        assertThat(result).contains("Acme Corp");
    }

    @Test
    void normalizeLine_stripsTagsThenCollapsesFinalWhitespace() {
        // Tags stripped → text may have extra spaces → then collapsed
        assertThat(normalizer.normalizeLine("<b>Hello</b>  World")).isEqualTo("Hello World");
    }

    @Test
    void normalizeLine_preservesAmpersandLiterally() {
        // Regression guard: the sanitizer must return plain text, not HTML —
        // an entity-encoded "&amp;" would silently corrupt every save and
        // break exact-match whitelist fields like `benefits` on resubmit.
        assertThat(normalizer.normalizeLine("Training & development"))
                .isEqualTo("Training & development");
    }

    @Test
    void normalizeLine_htmlOnlyInput_returnsNull() {
        // Markup with no text content normalizes to empty, then blank -> null.
        assertThat(normalizer.normalizeLine("<b></b>")).isNull();
        assertThat(normalizer.normalizeLine("<script>evil()</script>")).isNull();
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

    @Test
    void normalizeMultiline_stripsHtmlTags() {
        String input = "<p>Great culture.</p>\n<p>Inclusive team.</p>";
        String result = normalizer.normalizeMultiline(input);
        assertThat(result).doesNotContain("<p>");
        assertThat(result).contains("Great culture.");
        assertThat(result).contains("Inclusive team.");
    }

    @Test
    void normalizeMultiline_stripsScriptTags() {
        String input = "Good company.\n<script>stealData()</script>\nGreat benefits.";
        String result = normalizer.normalizeMultiline(input);
        assertThat(result).doesNotContain("<script>");
        assertThat(result).contains("Good company.");
        assertThat(result).contains("Great benefits.");
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

    @Test
    void normalizeList_stripsHtmlFromEntries() {
        List<String> input = Arrays.asList("<b>Engineering</b>", "Product");
        List<String> result = normalizer.normalizeList(input);
        assertThat(result).containsExactly("Engineering", "Product");
    }

    @Test
    void normalizeList_stripsScriptTagsFromEntries() {
        List<String> input = Arrays.asList("<script>evil()</script>HR", "Finance");
        List<String> result = normalizer.normalizeList(input);
        assertThat(result.get(0)).doesNotContain("<script>");
        assertThat(result).contains("Finance");
    }

    @Test
    void normalizeList_preservesAmpersandLiterally() {
        List<String> result = normalizer.normalizeList(List.of("Training & development"));
        assertThat(result).containsExactly("Training & development");
    }

    // ---------------------------------------------------- normalizeList canonicalization

    @Test
    void normalizeListWithCanonicalOptions_rewritesCasingToCanonicalForm() {
        List<String> options = List.of("Remote", "Hybrid", "On-site");
        List<String> result = normalizer.normalizeList(List.of("remote", "HYBRID"), options);
        assertThat(result).containsExactly("Remote", "Hybrid");
    }

    @Test
    void normalizeListWithCanonicalOptions_dedupesAcrossCasingVariants() {
        // "remote" and "Remote" canonicalize to the same string before dedup,
        // so only one survives — this is what keeps two admins who submit
        // different casing for the same checkbox from diverging in storage.
        List<String> options = List.of("Remote", "Hybrid");
        List<String> result = normalizer.normalizeList(List.of("remote", "Remote", "REMOTE"), options);
        assertThat(result).containsExactly("Remote");
    }

    @Test
    void normalizeListWithCanonicalOptions_nullOptionsBehavesAsPlainList() {
        List<String> result = normalizer.normalizeList(List.of("Java", "java"), null);
        assertThat(result).containsExactly("Java");
    }

    @Test
    void normalizeListWithCanonicalOptions_unmatchedEntryKeptVerbatim() {
        // Validation rejects genuinely unknown values before this runs; this
        // just documents that canonicalize() itself doesn't drop anything.
        List<String> options = List.of("Remote", "Hybrid");
        List<String> result = normalizer.normalizeList(List.of("On-site"), options);
        assertThat(result).containsExactly("On-site");
    }
}
