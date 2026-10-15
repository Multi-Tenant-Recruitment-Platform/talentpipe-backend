package com.talentpipe.candidate.validator;

import com.talentpipe.common.exception.InvalidRequestException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates and normalizes Sri Lankan National Identity Card (NIC) numbers.
 *
 * <p>Validation rules:</p>
 * <ol>
 *   <li><b>Pre-Processing:</b> Strips all whitespace, tabs, and hyphens; converts to uppercase.</li>
 *   <li><b>Structural Format:</b>
 *     <ul>
 *       <li>Pattern A (Old Format): Exactly 10 characters (9 digits followed by V or X).</li>
 *       <li>Pattern B (New Format): Exactly 12 characters (all digits, no letters).</li>
 *     </ul>
 *   </li>
 *   <li><b>Logical Range (Gender-Agnostic):</b>
 *     Isolates the 3-digit day-of-year sequence:
 *     <ul>
 *       <li>Old Format (10 chars): 3rd, 4th, 5th digits (index 2 to 5).</li>
 *       <li>New Format (12 chars): 5th, 6th, 7th digits (index 4 to 7).</li>
 *     </ul>
 *     The sequence must fall within Bracket 1 (001–366) or Bracket 2 (501–866).
 *   </li>
 * </ol>
 */
public final class CandidateNicValidator {

    private static final Pattern OLD_NIC_PATTERN = Pattern.compile("^\\d{9}[VX]$");
    private static final Pattern NEW_NIC_PATTERN = Pattern.compile("^\\d{12}$");

    private CandidateNicValidator() {
    }

    /**
     * Cleans and standardizes raw input:
     * - Strips all whitespace, tabs, and hyphens.
     * - Uppercases letters (e.g. 'v' -> 'V', 'x' -> 'X').
     */
    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replaceAll("[\\s-]+", "").toUpperCase(Locale.ROOT);
    }

    /**
     * Validates whether the given raw identity card number is valid.
     */
    public static boolean isValid(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String cleaned = clean(raw);
        return validateCleaned(cleaned) == null;
    }

    /**
     * Normalizes and validates the input NIC number.
     *
     * @param raw the user input
     * @return cleaned, uppercase normalized NIC string
     * @throws InvalidRequestException if blank, format invalid, or day sequence out of range
     */
    public static String normalizeAndValidate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRequestException("ID number cannot be blank.");
        }
        String cleaned = clean(raw);
        String errorMessage = validateCleaned(cleaned);
        if (errorMessage != null) {
            throw new InvalidRequestException(errorMessage);
        }
        return cleaned;
    }

    /**
     * Executes Step 1 (Regex structure) and Step 2 (Logical Range) validation on cleaned text.
     *
     * @param cleaned pre-processed string
     * @return null if valid, or an error message describing the violation
     */
    public static String validateCleaned(String cleaned) {
        if (cleaned == null || cleaned.isEmpty()) {
            return "ID number cannot be blank.";
        }

        int day;
        if (OLD_NIC_PATTERN.matcher(cleaned).matches()) {
            // Pattern A: 3rd, 4th, 5th digits (index 2 to 5)
            day = Integer.parseInt(cleaned.substring(2, 5));
        } else if (NEW_NIC_PATTERN.matcher(cleaned).matches()) {
            // Pattern B: 5th, 6th, 7th digits (index 4 to 7)
            day = Integer.parseInt(cleaned.substring(4, 7));
        } else {
            return "Format Invalid: ID number must be either 9 digits followed by V or X (old format) or 12 digits (new format).";
        }

        // Logical Range Rule (Gender-Agnostic)
        // Bracket 1: 001 - 366 (inclusive)
        // Bracket 2: 501 - 866 (inclusive)
        boolean inBracket1 = day >= 1 && day <= 366;
        boolean inBracket2 = day >= 501 && day <= 866;

        if (!inBracket1 && !inBracket2) {
            return "Invalid ID number: day sequence (" + String.format("%03d", day)
                    + ") is outside valid calendar day ranges (001-366 or 501-866).";
        }

        return null;
    }
}
