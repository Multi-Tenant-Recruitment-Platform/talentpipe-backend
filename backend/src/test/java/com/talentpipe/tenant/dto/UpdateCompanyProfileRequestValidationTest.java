package com.talentpipe.tenant.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Bean Validation constraints declared on
 * {@link UpdateCompanyProfileRequest}.
 *
 * <p>Uses the reference {@link Validator} implementation directly — no Spring
 * context is needed, making this fast and free of infrastructure dependencies.
 * A private factory method produces valid records; each test overrides a single
 * field to isolate the constraint under test.</p>
 */
class UpdateCompanyProfileRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Builds a minimal valid request. All required fields are populated;
     * all optional fields are null. This record must pass validation so tests
     * can modify a single field and assert a targeted violation.
     *
     * Field order matches the record declaration exactly (42 fields):
     *   name, tagline, industry, companyType, size,
     *   employeeCount, foundedYear, description, culture, mission, vision,
     *   legalName, registrationNumber, timezone, currency, language,
     *   email, hrEmail, phone, alternativePhone, website,
     *   linkedinUrl, facebookUrl, twitterUrl, instagramUrl,
     *   address, city, state, postalCode, country,
     *   values, benefits, workModes, officeLocations, departments,
     *   teams, businessUnits, employmentTypes, jobCategories, jobFamilies,
     *   jobLevels, jobTitles
     *
     * <p>Note: this helper's own parameter order is {@code (values, workModes,
     * benefits, ...)} for readability, but the record's canonical order is
     * {@code (values, benefits, workModes, ...)} — the constructor call below
     * reorders the two to match.</p>
     */
    private static UpdateCompanyProfileRequest req(
            String name, String tagline, String industry, String companyType, String size,
            Integer employeeCount, Integer foundedYear,
            String description, String culture, String mission, String vision,
            String legalName, String registrationNumber,
            String timezone, String currency, String language,
            String email, String hrEmail, String phone, String alternativePhone, String website,
            String linkedinUrl, String facebookUrl, String twitterUrl, String instagramUrl,
            String address, String city, String state, String postalCode, String country,
            List<String> values, List<String> workModes, List<String> benefits,
            List<String> officeLocations, List<String> departments, List<String> teams,
            List<String> businessUnits, List<String> employmentTypes,
            List<String> jobCategories, List<String> jobFamilies,
            List<String> jobLevels, List<String> jobTitles) {
        return new UpdateCompanyProfileRequest(
                name, tagline, industry, companyType, size,
                employeeCount, foundedYear,
                description, culture, mission, vision,
                legalName, registrationNumber,
                timezone, currency, language,
                email, hrEmail, phone, alternativePhone, website,
                linkedinUrl, facebookUrl, twitterUrl, instagramUrl,
                address, city, state, postalCode, country,
                values, benefits, workModes,
                officeLocations, departments, teams,
                businessUnits, employmentTypes,
                jobCategories, jobFamilies,
                jobLevels, jobTitles);
    }

    /** The minimal valid request baseline. */
    private static UpdateCompanyProfileRequest valid() {
        return req(
                "Acme Corp", null, null, null, null,
                null, null,
                null, null, null, null,
                null, null,
                null, null, null,
                "admin@acme.io", null, null, null, null,
                null, null, null, null,
                null, null, null, null, null,
                null, null, null,
                null, null, null,
                null, null,
                null, null,
                null, null);
    }

    private static Set<ConstraintViolation<UpdateCompanyProfileRequest>> validate(
            UpdateCompanyProfileRequest r) {
        return validator.validate(r);
    }

    private static boolean hasViolationOn(
            Set<ConstraintViolation<UpdateCompanyProfileRequest>> violations, String field) {
        return violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(field));
    }

    // ---------------------------------------------------------------- happy path

    @Test
    void validMinimalRequest_noViolations() {
        assertThat(validate(valid())).isEmpty();
    }

    // ---------------------------------------------------------------- name

    @Test
    void blankName_producesViolationOnName() {
        var r = req("", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        assertThat(hasViolationOn(validate(r), "name")).isTrue();
    }

    @Test
    void nameTooLong_producesViolationOnName() {
        var r = req("A".repeat(256), null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        assertThat(hasViolationOn(validate(r), "name")).isTrue();
    }

    // ---------------------------------------------------------------- email

    @Test
    void blankEmail_producesViolationOnEmail() {
        var r = req("Acme Corp", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        assertThat(hasViolationOn(validate(r), "email")).isTrue();
    }

    @Test
    void invalidEmailFormat_producesViolationOnEmail() {
        var r = req("Acme Corp", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "not-an-email", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        assertThat(hasViolationOn(validate(r), "email")).isTrue();
    }

    // ---------------------------------------------------------------- foundedYear

    @Test
    void foundedYearInFuture_producesViolation() {
        var r = req("Acme Corp", null, null, null, null, null, 9999, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        assertThat(hasViolationOn(validate(r), "foundedYear")).isTrue();
    }

    @Test
    void foundedYearBefore1800_producesViolation() {
        var r = req("Acme Corp", null, null, null, null, null, 1799, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        assertThat(hasViolationOn(validate(r), "foundedYear")).isTrue();
    }

    // ---------------------------------------------------------------- employeeCount

    @Test
    void employeeCountBelowMin_producesViolation() {
        var r = req("Acme Corp", null, null, null, null, 0, null, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        assertThat(hasViolationOn(validate(r), "employeeCount")).isTrue();
    }

    @Test
    void employeeCountAboveMax_producesViolation() {
        var r = req("Acme Corp", null, null, null, null, 5_000_001, null, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        assertThat(hasViolationOn(validate(r), "employeeCount")).isTrue();
    }

    // ---------------------------------------------------------------- description

    @Test
    void descriptionTooLong_producesViolation() {
        var r = req("Acme Corp", null, null, null, null, null, null, "D".repeat(1001), null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        assertThat(hasViolationOn(validate(r), "description")).isTrue();
    }

    // ---------------------------------------------------------------- phone

    @Test
    void invalidPhone_producesViolation() {
        var r = req("Acme Corp", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, "123", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        assertThat(hasViolationOn(validate(r), "phone")).isTrue();
    }

    // ---------------------------------------------------------------- URL

    @Test
    void invalidWebsiteUrl_producesViolation() {
        var r = req("Acme Corp", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, "not a url", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        assertThat(hasViolationOn(validate(r), "website")).isTrue();
    }

    // ---------------------------------------------------------------- workModes (checkbox @AllowedValues)

    @Test
    void unknownWorkMode_producesViolation() {
        var r = req("Acme Corp", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, List.of("Freelance"), null, null, null, null, null, null,
                null, null, null, null);
        assertThat(hasViolationOn(validate(r), "workModes")).isTrue();
    }

    @Test
    void validWorkModes_noViolation() {
        var r = req("Acme Corp", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, List.of("Remote", "Hybrid"), null, null, null, null, null, null,
                null, null, null, null);
        assertThat(hasViolationOn(validate(r), "workModes")).isFalse();
    }

    // ---------------------------------------------------------------- benefits (checkbox @AllowedValues)

    @Test
    void unknownBenefit_producesViolation() {
        var r = req("Acme Corp", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, List.of("Free lunch every day"), null, null, null, null, null,
                null, null, null, null);
        assertThat(hasViolationOn(validate(r), "benefits")).isTrue();
    }

    @Test
    void validBenefits_noViolation() {
        var r = req("Acme Corp", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, List.of("Health insurance", "Stock options"), null, null, null, null, null,
                null, null, null, null);
        assertThat(hasViolationOn(validate(r), "benefits")).isFalse();
    }

    /** benefits contains an ampersand ("Training & development") — a regression
     *  guard for the whitelist entry the sanitizer used to mangle before it was
     *  fixed to preserve plain text (see HtmlSanitizerTest). */
    @Test
    void benefitContainingAmpersand_isAccepted() {
        var r = req("Acme Corp", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, List.of("Training & development"), null, null, null, null, null,
                null, null, null, null);
        assertThat(hasViolationOn(validate(r), "benefits")).isFalse();
    }

    // ---------------------------------------------------------------- employmentTypes (checkbox @AllowedValues)

    @Test
    void unknownEmploymentType_producesViolation() {
        var r = req("Acme Corp", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null,
                null, List.of("Gig"), null, null, null, null);
        assertThat(hasViolationOn(validate(r), "employmentTypes")).isTrue();
    }

    // ---------------------------------------------------------------- jobLevels (checkbox @AllowedValues)

    @Test
    void unknownJobLevel_producesViolation() {
        var r = req("Acme Corp", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, List.of("Executive"), null);
        assertThat(hasViolationOn(validate(r), "jobLevels")).isTrue();
    }

    // ---------------------------------------------------------------- taxonomy tag-input lists (@ValidTaxonomyList)

    @Test
    void taxonomyListWith51Entries_producesViolation() {
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            tooMany.add("entry-" + i);
        }
        var r = req("Acme Corp", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                tooMany, null, null, null, null, null,
                null, null, null, null, null, null);
        assertThat(hasViolationOn(validate(r), "values")).isTrue();
    }

    @Test
    void taxonomyEntryExceeding100Chars_producesViolation() {
        var r = req("Acme Corp", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null,
                List.of("A".repeat(101)), null, null, null,
                null, null, null, null);
        assertThat(hasViolationOn(validate(r), "departments")).isTrue();
    }

    @Test
    void validTaxonomyList_noViolation() {
        var r = req("Acme Corp", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, "admin@acme.io", null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                List.of("Innovation", "Integrity"), null, null, null, null, null,
                null, null, null, null, null, null);
        assertThat(hasViolationOn(validate(r), "values")).isFalse();
    }
}
