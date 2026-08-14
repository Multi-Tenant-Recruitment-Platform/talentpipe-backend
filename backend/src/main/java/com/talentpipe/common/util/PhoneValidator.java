package com.talentpipe.common.util;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates phone numbers by counting digits after stripping allowed
 * separator characters. Matches the frontend validation rule in
 * {@code companyProfile.ts}: 7–15 digits after stripping non-digits.
 */
public class PhoneValidator implements ConstraintValidator<ValidPhone, String> {

    private static final int MIN_DIGITS = 7;
    private static final int MAX_DIGITS = 15;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // optional field — @NotBlank enforces mandatory
        }
        String digitsOnly = value.replaceAll("[^0-9]", "");
        return digitsOnly.length() >= MIN_DIGITS && digitsOnly.length() <= MAX_DIGITS;
    }
}
