package com.talentpipe.common.util;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string is a well-formed URL with a host component.
 * Accepts {@code null} and blank values (field is optional unless combined with
 * {@code @NotBlank}). Before persisting, the service normalizer prepends
 * {@code https://} when no scheme is present.
 */
@Documented
@Constraint(validatedBy = UrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidUrl {

    String message() default "must be a valid URL";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
