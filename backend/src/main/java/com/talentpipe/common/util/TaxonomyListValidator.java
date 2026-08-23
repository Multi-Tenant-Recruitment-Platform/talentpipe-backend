package com.talentpipe.common.util;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

/**
 * Validates taxonomy tag-input lists against configurable size and
 * per-entry length limits defined in {@link ValidTaxonomyList}.
 *
 * <p>Validation rules:</p>
 * <ul>
 *   <li>A {@code null} list is valid (optional field).</li>
 *   <li>An empty list is valid.</li>
 *   <li>List size must not exceed {@link ValidTaxonomyList#maxSize()}.</li>
 *   <li>Each non-null entry, after trimming, must not exceed
 *       {@link ValidTaxonomyList#maxEntryLength()} characters.</li>
 * </ul>
 *
 * <p>Blank entries are not rejected here — the service-layer
 * {@code ProfileNormalizer} filters them before persistence.</p>
 */
public class TaxonomyListValidator
        implements ConstraintValidator<ValidTaxonomyList, List<String>> {

    private int maxSize;
    private int maxEntryLength;

    @Override
    public void initialize(ValidTaxonomyList annotation) {
        this.maxSize = annotation.maxSize();
        this.maxEntryLength = annotation.maxEntryLength();
    }

    @Override
    public boolean isValid(List<String> values, ConstraintValidatorContext context) {
        if (values == null || values.isEmpty()) {
            return true;
        }
        if (values.size() > maxSize) {
            return false;
        }
        return values.stream()
                .filter(v -> v != null)
                .map(String::trim)
                .noneMatch(v -> v.length() > maxEntryLength);
    }
}
