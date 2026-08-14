package com.talentpipe.common.util;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Year;

/**
 * Validates that an integer year is not greater than the current calendar year.
 * The upper bound is evaluated at validation time, so no code change is needed
 * as years advance.
 */
public class MaxCurrentYearValidator implements ConstraintValidator<MaxCurrentYear, Integer> {

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // optional — use @NotNull to make mandatory
        }
        return value <= Year.now().getValue();
    }
}
