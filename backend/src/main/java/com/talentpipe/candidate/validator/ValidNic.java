package com.talentpipe.candidate.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates Sri Lankan National Identity Card (NIC) numbers.
 * Supports:
 * - Old format: 9 digits followed by V or X
 * - New format: 12 digits
 * Checks valid calendar day range (001-366 or 501-866).
 */
@Documented
@Constraint(validatedBy = NicConstraintValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidNic {

    String message() default "must be a valid identity card number (e.g. 200012345678 or 912345678V)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
