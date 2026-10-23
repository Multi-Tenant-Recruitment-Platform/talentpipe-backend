package com.talentpipe.common.util;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The single-value form of {@link AllowedValuesValidator}: a {@code String}
 * field must name one of the {@link AllowedValues#value()} options, matched
 * by {@link OptionKey}. {@code null} and blank are valid — the field is
 * optional unless combined with {@code @NotBlank}.
 */
public class AllowedValueValidator implements ConstraintValidator<AllowedValues, String> {

    private Set<String> allowedKeys;

    @Override
    public void initialize(AllowedValues annotation) {
        this.allowedKeys = Arrays.stream(annotation.value())
                .map(OptionKey::of)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || allowedKeys.contains(OptionKey.of(value));
    }
}
