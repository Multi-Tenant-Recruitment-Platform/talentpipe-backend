package com.talentpipe.candidate.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talentpipe.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CandidateNicValidatorTest {

    // ------------------------------------------------------------- 1. Pre-Processing

    @Test
    void cleanStripsWhitespaceTabsAndHyphensAndUppercases() {
        assertThat(CandidateNicValidator.clean(" 95123-4567-v \t")).isEqualTo("951234567V");
        assertThat(CandidateNicValidator.clean("1995 1234 5678")).isEqualTo("199512345678");
        assertThat(CandidateNicValidator.clean("1995-1234-5678")).isEqualTo("199512345678");
        assertThat(CandidateNicValidator.clean("912345678x")).isEqualTo("912345678X");
        assertThat(CandidateNicValidator.clean(null)).isNull();
    }

    // ------------------------------------------------------------- 2. Format Validation (Regex)

    @ParameterizedTest
    @ValueSource(strings = {
            "722441524V",
            "722441524v",
            "722441524X",
            "722441524x",
            " 95123-4567-V ",
            "951234567V"
    })
    void acceptsValidOldFormatPatternA(String nic) {
        assertThat(CandidateNicValidator.isValid(nic)).isTrue();
        assertThat(CandidateNicValidator.normalizeAndValidate(nic))
                .matches("^[0-9]{9}[VX]$")
                .hasSize(10);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "197224401524",
            "200012345678",
            " 1995 1234 5678 ",
            "1995-1234-5678"
    })
    void acceptsValidNewFormatPatternB(String nic) {
        assertThat(CandidateNicValidator.isValid(nic)).isTrue();
        assertThat(CandidateNicValidator.normalizeAndValidate(nic)).hasSize(12);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "cadidate",            // text from user screenshot
            "candidate",
            "12345678",            // too short
            "1234567890",          // 10 digits without V/X
            "722441524A",          // wrong suffix letter
            "19722440152V",        // 12 chars with letter
            "1972244015245",       // 13 digits
            "2000!@#$1234"         // invalid characters
    })
    void rejectsInvalidStructuralFormat(String invalidNic) {
        assertThat(CandidateNicValidator.isValid(invalidNic)).isFalse();
        assertThatThrownBy(() -> CandidateNicValidator.normalizeAndValidate(invalidNic))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Format Invalid");
    }

    // ------------------------------------------------------------- 3. Logical Day Range Validation

    @Test
    void acceptsBracket1BoundaryValues() {
        // Old format (day is index 2..5)
        // Day 001
        assertThat(CandidateNicValidator.isValid("950011234V")).isTrue();
        // Day 244
        assertThat(CandidateNicValidator.isValid("722441524V")).isTrue();
        // Day 366
        assertThat(CandidateNicValidator.isValid("963661234V")).isTrue();

        // New format (day is index 4..7)
        // Day 001
        assertThat(CandidateNicValidator.isValid("199500101234")).isTrue();
        // Day 366
        assertThat(CandidateNicValidator.isValid("199636601234")).isTrue();
    }

    @Test
    void acceptsBracket2BoundaryValues() {
        // Female day offset (+500)
        // Day 501 (Jan 1)
        assertThat(CandidateNicValidator.isValid("955011234V")).isTrue();
        // Day 744 (day 244 + 500)
        assertThat(CandidateNicValidator.isValid("727441524V")).isTrue();
        // Day 866 (Dec 31 in leap year)
        assertThat(CandidateNicValidator.isValid("968661234V")).isTrue();

        // New format Bracket 2
        assertThat(CandidateNicValidator.isValid("199550101234")).isTrue();
        assertThat(CandidateNicValidator.isValid("199686601234")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "950001234V",       // day = 000
            "953671234V",       // day = 367
            "954501234V",       // day = 450
            "955001234V",       // day = 500
            "958671234V",       // day = 867
            "959991234V",       // day = 999
            "199500001234",     // new format day = 000
            "199536701234",     // new format day = 367
            "199550001234",     // new format day = 500
            "199586701234",     // new format day = 867
            "199599901234"      // new format day = 999
    })
    void rejectsInvalidDayRanges(String nicWithBadDay) {
        assertThat(CandidateNicValidator.isValid(nicWithBadDay)).isFalse();
        assertThatThrownBy(() -> CandidateNicValidator.normalizeAndValidate(nicWithBadDay))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("outside valid calendar day ranges");
    }

    // ------------------------------------------------------------- 4. Blank & Null Inputs

    @Test
    void rejectsBlankAndNullInputs() {
        assertThat(CandidateNicValidator.isValid(null)).isFalse();
        assertThat(CandidateNicValidator.isValid("   ")).isFalse();
        assertThat(CandidateNicValidator.isValid("")).isFalse();

        assertThatThrownBy(() -> CandidateNicValidator.normalizeAndValidate(null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("cannot be blank");

        assertThatThrownBy(() -> CandidateNicValidator.normalizeAndValidate("  "))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("cannot be blank");
    }
}
