package com.talentpipe.candidate.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NicConstraintValidatorTest {

    private NicConstraintValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @BeforeEach
    void setUp() {
        validator = new NicConstraintValidator();
    }

    @Test
    void isValid_nullOrBlank_returnsTrue() {
        assertThat(validator.isValid(null, context)).isTrue();
        assertThat(validator.isValid("", context)).isTrue();
        assertThat(validator.isValid("   ", context)).isTrue();
    }

    @Test
    void isValid_validOldAndNewNic_returnsTrue() {
        assertThat(validator.isValid("722441524V", context)).isTrue();
        assertThat(validator.isValid("197224401524", context)).isTrue();
    }

    @Test
    void isValid_invalidNic_buildsViolationAndReturnsFalse() {
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);

        boolean result = validator.isValid("invalid-nic", context);

        assertThat(result).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(violationBuilder).addConstraintViolation();
    }
}
