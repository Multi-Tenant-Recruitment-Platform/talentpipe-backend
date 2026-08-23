package com.talentpipe.common.util;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that every entry in a {@code List<String>} is one of the
 * explicitly permitted values.
 *
 * <p>Designed for fields that are driven by fixed checkboxes on the frontend
 * (e.g. {@code workModes}, {@code benefits}, {@code employmentTypes},
 * {@code jobLevels}). Sending an unknown value — e.g. due to a tampered
 * request — results in a 400 Bad Request before the service layer is reached.
 *
 * <p>Matching is case-insensitive so that normalised and un-normalised casing
 * from the frontend are both accepted. A {@code null} or empty list is valid;
 * the field is optional unless combined with another constraint.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * @AllowedValues(
 *     value = {"Remote", "Hybrid", "On-site"},
 *     message = "workModes must be one of: Remote, Hybrid, On-site"
 * )
 * List<String> workModes,
 * }</pre>
 */
@Documented
@Constraint(validatedBy = AllowedValuesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedValues {

    /**
     * The exhaustive list of permitted string values (case-insensitive match).
     * Must not be empty.
     */
    String[] value();

    String message() default "contains one or more invalid values";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
