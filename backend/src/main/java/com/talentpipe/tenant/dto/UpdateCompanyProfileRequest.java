package com.talentpipe.tenant.dto;

import com.talentpipe.common.util.AllowedValues;
import com.talentpipe.common.util.MaxCurrentYear;
import com.talentpipe.common.util.ValidPhone;
import com.talentpipe.common.util.ValidTaxonomyList;
import com.talentpipe.common.util.ValidUrl;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request body for {@code PATCH /api/v1/tenant}.
 *
 * <p>Contains only the fields that COMPANY_ADMIN may edit. Identity and billing
 * fields ({@code id}, {@code subdomain}, {@code planTier}, {@code status}) are
 * deliberately absent — the service layer ignores any attempt to update them
 * through a different channel (e.g. raw SQL), and Jackson simply discards
 * unknown fields in the request body.</p>
 *
 * <p>Validation constraints mirror the frontend rules in {@code companyProfile.ts}
 * and are the server-side source of truth. See {@code ProfileNormalizer} for
 * the normalization that runs after validation and before persistence.</p>
 *
 * <p>All optional fields accept {@code null}, which means "clear this value".
 * An empty string is treated as null by the normalizer.</p>
 *
 * <h3>List field taxonomy</h3>
 * <ul>
 *   <li><strong>Checkbox-driven</strong> ({@code workModes}, {@code benefits},
 *       {@code employmentTypes}, {@code jobLevels}): validated against a
 *       {@link AllowedValues} whitelist that matches the fixed frontend
 *       checkboxes ({@link ProfileTaxonomy}). Unknown values are rejected
 *       with 400; accepted values are canonicalized to the casing declared
 *       there before persistence.</li>
 *   <li><strong>Tag-input</strong> ({@code values}, {@code officeLocations},
 *       {@code departments}, {@code teams}, {@code businessUnits},
 *       {@code jobCategories}, {@code jobFamilies}, {@code jobTitles}):
 *       validated with {@link ValidTaxonomyList} — max 50 entries, max 100
 *       characters per entry.</li>
 * </ul>
 */
public record UpdateCompanyProfileRequest(

        // ---- required fields
        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        // ---- branding
        @Size(max = 140, message = "tagline must be at most 140 characters")
        String tagline,

        // ---- company info
        @Size(max = 100, message = "industry must be at most 100 characters")
        String industry,

        @Size(max = 100, message = "companyType must be at most 100 characters")
        String companyType,

        @Size(max = 100, message = "size must be at most 100 characters")
        String size,

        @Min(value = 1, message = "employeeCount must be at least 1")
        @Max(value = 5_000_000, message = "employeeCount must be at most 5,000,000")
        Integer employeeCount,

        @Min(value = 1800, message = "foundedYear must be 1800 or later")
        @MaxCurrentYear(message = "foundedYear must not be a future year")
        Integer foundedYear,

        @Size(max = 1000, message = "description must be at most 1000 characters")
        String description,

        @Size(max = 600, message = "culture must be at most 600 characters")
        String culture,

        @Size(max = 400, message = "mission must be at most 400 characters")
        String mission,

        @Size(max = 400, message = "vision must be at most 400 characters")
        String vision,

        @Size(max = 255, message = "legalName must be at most 255 characters")
        String legalName,

        @Size(max = 60, message = "registrationNumber must be at most 60 characters")
        String registrationNumber,

        // ---- localisation
        @Size(max = 100, message = "timezone must be at most 100 characters")
        String timezone,

        @Size(max = 10, message = "currency must be at most 10 characters")
        String currency,

        @Size(max = 10, message = "language must be at most 10 characters")
        String language,

        // ---- contact
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @Email(message = "hrEmail must be a valid email address")
        @Size(max = 255, message = "hrEmail must be at most 255 characters")
        String hrEmail,

        @ValidPhone
        String phone,

        @ValidPhone
        String alternativePhone,

        @ValidUrl
        String website,

        @ValidUrl
        String linkedinUrl,

        @ValidUrl
        String facebookUrl,

        @ValidUrl
        String twitterUrl,

        @ValidUrl
        String instagramUrl,

        // ---- location
        @Size(max = 255, message = "address must be at most 255 characters")
        String address,

        @Size(max = 120, message = "city must be at most 120 characters")
        String city,

        @Size(max = 120, message = "state must be at most 120 characters")
        String state,

        @Size(max = 60, message = "postalCode must be at most 60 characters")
        String postalCode,

        @Size(max = 120, message = "country must be at most 120 characters")
        String country,

        // ---- free-text tag-input lists (max 50 entries, max 100 chars each)
        @ValidTaxonomyList
        List<String> values,

        // ---- checkbox-driven list: fixed options only. Catalogue ids, not
        // labels — see ProfileTaxonomy.BENEFITS for why, and keep the two in
        // sync (ProfileTaxonomyDriftTest guards this).
        @AllowedValues(
                value = {
                        "REMOTE_HYBRID", "FLEXIBLE_HOURS",
                        "HEALTH_INSURANCE", "TRAINING",
                        "PAID_LEAVE", "PARENTAL_LEAVE",
                        "PERFORMANCE_BONUS", "STOCK_OPTIONS",
                        "WELLBEING", "TRANSPORT",
                        "MEALS", "RELOCATION",
                        "CAREER_DEVELOPMENT"
                },
                message = "benefits contains an unrecognised option")
        List<String> benefits,

        // ---- checkbox-driven list: keep in sync with ProfileTaxonomy.WORK_MODES
        @AllowedValues(
                value = {"Remote", "Hybrid", "On-site"},
                message = "workModes must be one of: Remote, Hybrid, On-site")
        List<String> workModes,

        // ---- free-text tag-input lists
        @ValidTaxonomyList
        List<String> officeLocations,

        @ValidTaxonomyList
        List<String> departments,

        @ValidTaxonomyList
        List<String> teams,

        @ValidTaxonomyList
        List<String> businessUnits,

        // ---- checkbox-driven list: keep in sync with ProfileTaxonomy.EMPLOYMENT_TYPES
        @AllowedValues(
                value = {"Full-time", "Part-time", "Contract",
                        "Internship", "Temporary", "Freelance"},
                message = "employmentTypes must be one of: Full-time, Part-time, Contract, Internship, Temporary, Freelance")
        List<String> employmentTypes,

        // ---- free-text tag-input lists
        @ValidTaxonomyList
        List<String> jobCategories,

        @ValidTaxonomyList
        List<String> jobFamilies,

        // ---- checkbox-driven list: keep in sync with ProfileTaxonomy.JOB_LEVELS
        @AllowedValues(
                value = {"Intern", "Junior", "Mid-level", "Senior",
                        "Lead", "Manager", "Director"},
                message = "jobLevels must be one of: Intern, Junior, Mid-level, Senior, Lead, Manager, Director")
        List<String> jobLevels,

        // ---- free-text tag-input list
        @ValidTaxonomyList
        List<String> jobTitles
) {
}
