package com.talentpipe.common.util;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Validates that a string is a URL with a non-empty host.
 *
 * <p>Strategy: normalize to lowercase, prepend {@code https://} when the
 * raw value has no scheme (the service layer also normalizes before persist,
 * so the URL a user types without a scheme must still validate). Null and
 * blank values are accepted — optionality is enforced separately via
 * {@code @NotBlank} when required.</p>
 */
public class UrlValidator implements ConstraintValidator<ValidUrl, String> {

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
            return uri.getHost() != null && !uri.getHost().isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
