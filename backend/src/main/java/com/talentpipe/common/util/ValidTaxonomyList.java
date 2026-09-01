package com.talentpipe.common.util;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a taxonomy {@code List<String>} does not exceed the
 * configured size limit and that no individual entry exceeds the configured
 * character limit.
 *
 * <p>Applied to free-text tag-input fields on the profile form (e.g.
 * {@code values}, {@code departments}, {@code teams}, {@code officeLocations}).
 * These fields allow users to type arbitrary strings; this constraint enforces
 * reasonable server-side bounds to prevent abuse and protect database
 * storage.</p>
 *
 * <p>A {@code null} list is valid (treated as empty by the service
 * normaliser). Blank entries within the list are ignored — the normaliser
 * filters them before persistence.</p>
 *
 * <p>Usage (default limits — 50 entries, 100 chars each):</p>
 * <pre>{@code
 * @ValidTaxonomyList
 * List<String> departments,
 * }</pre>
 *
 * <p>Override limits for a specific field:</p>
 * <pre>{@code
 * @ValidTaxonomyList(maxSize = 20, maxEntryLength = 60)
 * List<String> officeLocations,
 * }</pre>
 */
@Documented
@Constraint(validatedBy = TaxonomyListValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTaxonomyList {

    /** Maximum number of entries allowed in the list. */
    int maxSize() default 50;

    /** Maximum character length for any single entry. */
    int maxEntryLength() default 100;

    String message() default "list exceeds maximum size or contains an entry that is too long";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
