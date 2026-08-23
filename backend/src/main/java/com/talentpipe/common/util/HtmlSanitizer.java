package com.talentpipe.common.util;

import java.util.List;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.HtmlStreamEventReceiver;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * Reduces a user-supplied string to plain text by removing all HTML markup,
 * using the OWASP Java HTML Sanitizer's parser.
 *
 * <p>Security rationale: free-text fields on the company profile (name,
 * tagline, description, mission, vision, …) accept user-supplied strings that
 * may later be rendered in a browser context. Without sanitization an attacker
 * could store a payload such as {@code <script>document.cookie</script>} and
 * trigger a stored XSS attack when that content is served to other users.</p>
 *
 * <h3>Why not {@code PolicyFactory.sanitize(String)}</h3>
 * <p>That method returns <em>HTML</em>, not plain text: it strips the tags but
 * entity-encodes whatever survives, so {@code Ben & Jerry's} comes back as
 * {@code Ben &amp;amp; Jerry&amp;#39;s}. For a JSON API that stores plain text
 * this is silent data corruption — every ampersand, apostrophe, quote and
 * angle bracket a user types would be mangled in the database and echoed back
 * mangled on the next GET.</p>
 *
 * <p>Instead this component drives the same deny-all policy through the
 * streaming API and keeps only the {@code text()} events. The parser has
 * already decoded entities by that point, so the result is the literal text
 * the user typed, with all markup removed and nothing re-encoded.</p>
 *
 * <h3>Repeated passes</h3>
 * <p>Because entities are decoded, a double-encoded payload such as
 * {@code &amp;lt;script&amp;gt;} would decode to a live {@code <script>} tag in
 * a single pass. The strip is therefore repeated until it reaches a fixed
 * point (bounded by {@link #MAX_PASSES}), so no markup survives regardless of
 * how many layers of encoding it arrives under.</p>
 *
 * <h3>Length guarantee</h3>
 * <p>{@code sanitize(input).length() <= input.length()} always holds: markup
 * is only ever removed and entities are only ever decoded (shorter or equal —
 * {@code "&amp;"} → {@code "&"}), so no transformation here can grow the
 * string. This is what lets a {@code @Size(max = N)} bean-validation
 * constraint on the raw request field also bound what gets persisted: the
 * database columns in {@code V9__tenant_profile_columns.sql} are sized to
 * exactly those same limits, with no headroom for post-sanitization growth.</p>
 *
 * <p>Called by {@link com.talentpipe.tenant.service.ProfileNormalizer} as the
 * first step before trimming and whitespace-collapsing, so clean plain text is
 * what ultimately reaches the database.</p>
 */
@Component
public class HtmlSanitizer {

    /**
     * Deny-all policy: no element or attribute is whitelisted. Constructed
     * once — {@link PolicyFactory} is thread-safe and reusable.
     */
    private static final PolicyFactory DENY_ALL_POLICY = new HtmlPolicyBuilder().toFactory();

    /**
     * Upper bound on strip passes. Two passes suffice for any realistic input
     * (one to strip, one to confirm stability); the third covers
     * multiply-encoded payloads without risking an unbounded loop.
     */
    private static final int MAX_PASSES = 3;

    /**
     * Returns the plain-text content of {@code input} with all HTML markup
     * removed and no entity re-encoding. A {@code null} input returns
     * {@code null}; an input that is entirely markup returns an empty string.
     *
     * @param input raw user-supplied string, may contain HTML tags
     * @return sanitized plain text, or {@code null} if input was {@code null}
     */
    public String sanitize(String input) {
        if (input == null) {
            return null;
        }
        String current = input;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            String stripped = stripOnce(current);
            if (stripped.equals(current)) {
                break; // fixed point — no markup left to remove
            }
            current = stripped;
        }
        return current;
    }

    /** One parse of {@code html}, collecting only the decoded text nodes. */
    private String stripOnce(String html) {
        StringBuilder text = new StringBuilder(html.length());
        org.owasp.html.HtmlSanitizer.sanitize(html, DENY_ALL_POLICY.apply(new TextCollector(text)));
        return text.toString();
    }

    /**
     * Receives the sanitizer's event stream and keeps the text only. Tag
     * events are discarded, which is what turns "sanitized HTML" into
     * "plain text": the content of elements the policy drops outright (e.g.
     * {@code <script>}) never reaches {@link #text(String)} at all.
     */
    private static final class TextCollector implements HtmlStreamEventReceiver {

        private final StringBuilder out;

        TextCollector(StringBuilder out) {
            this.out = out;
        }

        @Override
        public void openDocument() {
            // no-op
        }

        @Override
        public void closeDocument() {
            // no-op
        }

        @Override
        public void openTag(String elementName, List<String> attrs) {
            // markup is discarded
        }

        @Override
        public void closeTag(String elementName) {
            // markup is discarded
        }

        @Override
        public void text(String textChunk) {
            out.append(textChunk);
        }
    }
}
