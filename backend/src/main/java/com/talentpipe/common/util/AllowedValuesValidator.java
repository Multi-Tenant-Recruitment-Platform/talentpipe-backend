package com.talentpipe.common.util;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates that every non-null element in a {@code List<String>} is a member
 * of the {@link AllowedValues#value()} whitelist.
 *
 * <p>Matching is case-insensitive: the candidate value is lowercased and
 * compared against a lowercased copy of the whitelist. This tolerates minor
 * casing differences between the frontend and what the backend stores after
 * normalisation, without silently accepting genuinely invalid values.</p>
 *
 * <p>A {@code null} list or an empty list is considered valid — use
 * {@code @NotEmpty} on top if the field is mandatory.</p>
 */
public class AllowedValuesValidator
        implements ConstraintValidator<AllowedValues, List<String>> {

    private Set<String> allowedLowercase;

    @Override
    public void initialize(AllowedValues annotation) {
        this.allowedLowercase = Arrays.stream(annotation.value())
                .map(v -> v.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns {@code true} when:
     * <ul>
     *   <li>the list is {@code null} or empty, or</li>
     *   <li>every non-null element's lowercase form is in the whitelist.</li>
     * </ul>
     */
    @Override
    public boolean isValid(List<String> values, ConstraintValidatorContext context) {
        if (values == null || values.isEmpty()) {
            return true;
        }
        return values.stream()
                .filter(v -> v != null)
                .map(v -> v.toLowerCase(Locale.ROOT))
                .allMatch(allowedLowercase::contains);
    }
}
