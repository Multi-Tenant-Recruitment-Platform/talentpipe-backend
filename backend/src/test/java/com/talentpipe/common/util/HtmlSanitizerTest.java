package com.talentpipe.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link HtmlSanitizer}.
 * No Spring context required — the component is a POJO with a static policy.
 */
class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    // ---------------------------------------------------------------- null / blank

    @Test
    void null_returnsNull() {
        assertThat(sanitizer.sanitize(null)).isNull();
    }

    @Test
    void emptyString_returnsEmpty() {
        assertThat(sanitizer.sanitize("")).isEqualTo("");
    }

    @Test
    void blankString_returnsBlank() {
        assertThat(sanitizer.sanitize("   ")).isEqualTo("   ");
    }

    // ---------------------------------------------------------------- plain text

    @Test
    void plainText_isUnchanged() {
        assertThat(sanitizer.sanitize("Acme Corp")).isEqualTo("Acme Corp");
    }

    @Test
    void plainText_withAmpersand_isPreservedLiterally() {
        // The sanitizer must return plain text, not HTML: an ampersand the
        // user typed must come back as "&", not the HTML entity "&amp;" —
        // otherwise every subsequent read (and, for whitelist fields like
        // `benefits`, every subsequent write) sees a different string than
        // what was submitted. See HtmlSanitizer's class javadoc.
        assertThat(sanitizer.sanitize("R&D / Finance — 2026"))
                .isEqualTo("R&D / Finance — 2026");
    }

    @Test
    void plainText_withQuotesAndAngleBrackets_isPreservedLiterally() {
        assertThat(sanitizer.sanitize("Revenue > 5M, cost < 2M"))
                .isEqualTo("Revenue > 5M, cost < 2M");
        assertThat(sanitizer.sanitize("Smith \"Bob\" Jones"))
                .isEqualTo("Smith \"Bob\" Jones");
    }

    // ---------------------------------------------------------------- double-encoded payloads

    @Test
    void doubleEncodedScriptTag_isFullyStripped() {
        // A single decode pass would turn this into a live <script> tag.
        String input = "&lt;script&gt;alert(1)&lt;/script&gt;";
        assertThat(sanitizer.sanitize(input)).doesNotContain("<script>");
    }

    @Test
    void tripleEncodedScriptTag_isFullyStripped() {
        String input = "&amp;lt;script&amp;gt;alert(1)&amp;lt;/script&amp;gt;";
        assertThat(sanitizer.sanitize(input)).doesNotContain("<script>");
    }

    // ---------------------------------------------------------------- XSS payloads stripped

    @Test
    void scriptTag_isStripped() {
        String input = "<script>alert('xss')</script>";
        assertThat(sanitizer.sanitize(input)).doesNotContain("<script>");
        assertThat(sanitizer.sanitize(input)).doesNotContain("</script>");
    }

    @Test
    void scriptTag_preservesTextContent() {
        String input = "<script>evil()</script>Some text";
        assertThat(sanitizer.sanitize(input)).contains("Some text");
    }

    @Test
    void imgOnErrorXss_isStripped() {
        String input = "<img src=x onerror=alert(1)>";
        assertThat(sanitizer.sanitize(input)).doesNotContain("<img");
        assertThat(sanitizer.sanitize(input)).doesNotContain("onerror");
    }

    @Test
    void onClickAttribute_isStripped() {
        String input = "<div onclick=\"stealCookies()\">Click me</div>";
        assertThat(sanitizer.sanitize(input)).doesNotContain("onclick");
        assertThat(sanitizer.sanitize(input)).doesNotContain("<div");
    }

    @Test
    void iframeTag_isStripped() {
        String input = "<iframe src=\"https://evil.com\"></iframe>";
        assertThat(sanitizer.sanitize(input)).doesNotContain("<iframe");
    }

    // ---------------------------------------------------------------- HTML formatting tags stripped

    @Test
    void boldTag_contentPreserved_tagsStripped() {
        assertThat(sanitizer.sanitize("<b>bold text</b>")).contains("bold text");
        assertThat(sanitizer.sanitize("<b>bold text</b>")).doesNotContain("<b>");
    }

    @Test
    void nestedTags_contentPreserved_tagsStripped() {
        String input = "<div><p><strong>Nested</strong></p></div>";
        String result = sanitizer.sanitize(input);
        assertThat(result).contains("Nested");
        assertThat(result).doesNotContain("<div>");
        assertThat(result).doesNotContain("<p>");
        assertThat(result).doesNotContain("<strong>");
    }

    // ---------------------------------------------------------------- multiline

    @Test
    void multilineTextWithHtml_tagsStripped_newlinesPreserved() {
        String input = "Line one\n<script>evil()</script>\nLine three";
        String result = sanitizer.sanitize(input);
        assertThat(result).contains("Line one");
        assertThat(result).contains("Line three");
        assertThat(result).doesNotContain("<script>");
    }

    // ---------------------------------------------------------------- length invariant

    /**
     * Load-bearing for DB safety: {@code @Size(max = N)} on the raw request
     * field is only a valid bound on what gets persisted if sanitizing can
     * never grow the string. The database columns have no headroom beyond
     * their {@code @Size} limit (see V9__tenant_profile_columns.sql), so a
     * sanitizer that expands input — as entity-encoding one used to — would
     * let validated input overflow its column.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "&", "&&&&&", "\"\"\"\"\"", "<><><><>",
            "<b>&amp;&amp;&amp;</b>", "&lt;script&gt;", "plain ascii text",
            "Café — naïve 日本語"
    })
    void sanitizedOutput_isNeverLongerThanInput(String input) {
        assertThat(sanitizer.sanitize(input).length()).isLessThanOrEqualTo(input.length());
    }
}
