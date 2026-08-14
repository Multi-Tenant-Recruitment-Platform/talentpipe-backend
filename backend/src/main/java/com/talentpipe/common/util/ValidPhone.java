package com.talentpipe.common.util;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string is a plausible phone number: 7–15 digits after
 * stripping common separator characters ({@code +}, {@code -}, spaces,
 * parentheses). Accepts {@code null} and blank (field is optional).
 */
@Documented
@Constraint(validatedBy = PhoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhone {

    String message() default "must be a valid phone number (7\u201315 digits)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
