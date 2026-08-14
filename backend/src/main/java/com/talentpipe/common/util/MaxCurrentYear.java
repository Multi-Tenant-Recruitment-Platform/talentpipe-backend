package com.talentpipe.common.util;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that an integer year does not exceed the current calendar year.
 * Used for {@code foundedYear} where 1800 is the minimum (enforced by
 * {@code @Min(1800)}) and the upper bound is dynamic (cannot be in the future).
 * Accepts {@code null} (field is optional).
 */
@Documented
@Constraint(validatedBy = MaxCurrentYearValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxCurrentYear {

    String message() default "must not be a future year";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
