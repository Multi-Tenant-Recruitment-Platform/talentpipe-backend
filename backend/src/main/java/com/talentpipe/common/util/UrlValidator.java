package com.talentpipe.common.util;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * Validates that a string is an {@code http}/{@code https} URL with a
 * non-empty host.
 *
 * <p>Strategy: prepend {@code https://} when the raw value has no scheme (the
 * service layer also normalizes before persist, so a URL a user types without
 * a scheme must still validate), then require both a recognised scheme and a
 * host. Null and blank values are accepted — optionality is enforced
 * separately via {@code @NotBlank} when required.</p>
 *
 * <p>Security: the scheme whitelist is the point of this validator, not a
 * nicety. These values are stored and later rendered as {@code href}
 * attributes on the public company page, so accepting {@code javascript:} or
 * {@code vbscript:} would be a stored XSS vector — {@code
 * javascript://evil.com/%0aalert(1)} parses as a perfectly well-formed URI
 * with the host {@code evil.com}. Only {@code http} and {@code https} are
 * permitted; every other scheme (including {@code ftp}, {@code file} and
 * {@code data}) is rejected.</p>
 */
public class UrlValidator implements ConstraintValidator<ValidUrl, String> {

    /** The only schemes safe to render as a link target. */
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // null / blank accepted — use @NotBlank to make mandatory
        }
        String candidate = value.trim();
        // Prepend scheme so bare hosts like "talentpipe.io" still parse.
        if (!candidate.contains("://")) {
            candidate = "https://" + candidate;
        }
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
                return false;
            }
            return uri.getHost() != null && !uri.getHost().isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
