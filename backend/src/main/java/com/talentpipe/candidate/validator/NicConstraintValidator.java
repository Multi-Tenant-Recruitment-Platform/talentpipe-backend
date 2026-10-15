package com.talentpipe.candidate.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Bean Validation constraint validator for {@link ValidNic}.
 */
public class NicConstraintValidator implements ConstraintValidator<ValidNic, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // Let @NotBlank handle presence checks
        }
        String error = CandidateNicValidator.validateCleaned(CandidateNicValidator.clean(value));
        if (error != null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(error).addConstraintViolation();
            return false;
        }
        return true;
    }
}
