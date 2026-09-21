package com.talentpipe.common.util;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates that every meaningful element in a {@code List<String>} names one
 * of the {@link AllowedValues#value()} options.
 *
 * <p>Matching is by {@link OptionKey}, not by string equality: case,
 * {@code &} vs {@code and}, and all punctuation and spacing are folded away,
 * so a client posting {@code "REMOTE_HYBRID"}, {@code "remote_hybrid"} or
 * {@code "Remote-Hybrid"} all pass, while an option that is genuinely not on
 * the list still fails. {@code ProfileNormalizer} then rewrites whatever
 * arrived to the canonical spelling, so the tolerance here never reaches the
 * database.</p>
 *
 * <p>A {@code null} list, an empty list, and {@code null} or blank entries are
 * all valid — blank entries are dropped by {@code ProfileNormalizer} before
 * persistence, so rejecting them here would only turn a harmless empty
 * checkbox value into a 400. Use {@code @NotEmpty} on top if the field itself
 * is mandatory.</p>
 *
 * <p>When values are rejected the violation message names them, so the client
 * sees which option was not recognised rather than only that one wasn't.</p>
 */
public class AllowedValuesValidator
        implements ConstraintValidator<AllowedValues, List<String>> {

    /** Cap on how many rejected values are echoed back in the message. */
    private static final int MAX_REPORTED = 3;

    /** Cap on the length of a single echoed value, to bound the message. */
    private static final int MAX_REPORTED_LENGTH = 60;

    private Set<String> allowedKeys;
    private String message;

    @Override
    public void initialize(AllowedValues annotation) {
        this.allowedKeys = Arrays.stream(annotation.value())
                .map(OptionKey::of)
                .collect(Collectors.toUnmodifiableSet());
        this.message = annotation.message();
    }

    /**
     * Returns {@code true} when every element that is neither {@code null} nor
     * blank has a key in the whitelist; otherwise {@code false}, after
     * replacing the default violation with one that names the offenders.
     */
    @Override
    public boolean isValid(List<String> values, ConstraintValidatorContext context) {
        if (values == null || values.isEmpty()) {
            return true;
        }
        List<String> rejected = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue; // dropped by ProfileNormalizer, not an unknown option
            }
            if (!allowedKeys.contains(OptionKey.of(value))) {
                rejected.add(value);
            }
        }
        if (rejected.isEmpty()) {
            return true;
        }
        reportRejected(rejected, context);
        return false;
    }

    /**
     * Replaces the default violation with the annotation's message plus the
     * offending values, e.g. {@code "benefits contains an unrecognised option
     * (rejected: 'Free coffee')"}.
     */
    private void reportRejected(List<String> rejected, ConstraintValidatorContext context) {
        if (context == null) {
            return; // unit-test call site with no context
        }
        String detail = rejected.stream()
                .limit(MAX_REPORTED)
                .map(value -> "'" + forMessage(value) + "'")
                .collect(Collectors.joining(", "));
        if (rejected.size() > MAX_REPORTED) {
            detail += ", …";
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                        message + " (rejected: " + detail + ")")
                .addConstraintViolation();
    }

    /**
     * Makes a rejected value safe to embed in a message template: EL and
     * interpolation metacharacters are stripped rather than escaped, and the
     * value is truncated, so echoed user input can neither break the template
     * nor bloat the response.
     */
    private static String forMessage(String value) {
        String cleaned = value.replaceAll("[{}$\\\\']", "");
        return cleaned.length() > MAX_REPORTED_LENGTH
                ? cleaned.substring(0, MAX_REPORTED_LENGTH) + "…"
                : cleaned;
    }
}
