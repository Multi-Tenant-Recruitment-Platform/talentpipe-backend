package com.talentpipe.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The editable subset of a company profile (PB-005).
 *
 * <p>Identity and billing fields — {@code id}, {@code subdomain},
 * {@code planTier}, {@code status} — are deliberately absent. They are not the
 * admin's to change from this screen, so accepting them would invite a service
 * that trusts them. The images are absent too: they have their own endpoints
 * because they are multipart, and a text save must never be able to clear one.</p>
 *
 * <p>Semantics are full replacement of the fields listed here, matching what
 * the settings form submits: it always sends the complete editable set, so a
 * field arriving as null means "cleared", not "unchanged". Every string bound
 * mirrors its column width in V8 so a value can never be accepted by validation
 * and then rejected by the database.</p>
 */
public record UpdateCompanyProfileRequest(

        @NotBlank(message = "name is required")
        @Size(max = 255)
        String name,

        @Size(max = 255) String tagline,
        @Size(max = 100) String industry,
        @Size(max = 100) String companyType,
        @Size(max = 50) String size,

        @Min(value = 0, message = "must not be negative")
        @Max(value = 10_000_000, message = "must be realistic")
        Integer employeeCount,

        // Upper bound is deliberately loose rather than "this year": a clock or
        // timezone edge must never reject a company founded today.
        @Min(value = 1800, message = "must be a four-digit year after 1800")
        @Max(value = 2200, message = "must be a four-digit year")
        Integer foundedYear,

        String description,
        String culture,
        String mission,
        String vision,

        List<@Size(max = 200) String> values,
        List<@Size(max = 200) String> benefits,

        @Size(max = 255) String legalName,
        @Size(max = 100) String registrationNumber,
        List<@Size(max = 100) String> workModes,

        @Size(max = 64) String timezone,
        @Size(max = 10) String currency,
        @Size(max = 50) String language,

        @Email(message = "must be a valid email address")
        @Size(max = 255)
        String email,

        @Email(message = "must be a valid email address")
        @Size(max = 255)
        String hrEmail,

        @Size(max = 50) String phone,
        @Size(max = 50) String alternativePhone,
        @Size(max = 255) String website,
        @Size(max = 255) String linkedinUrl,
        @Size(max = 255) String facebookUrl,
        @Size(max = 255) String twitterUrl,
        @Size(max = 255) String instagramUrl,

        @Size(max = 500) String address,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 20) String postalCode,
        @Size(max = 100) String country,
        List<@Size(max = 200) String> officeLocations,

        List<@Size(max = 150) String> departments,
        List<@Size(max = 150) String> teams,
        List<@Size(max = 150) String> businessUnits,
        List<@Size(max = 100) String> employmentTypes,
        List<@Size(max = 150) String> jobCategories,
        List<@Size(max = 150) String> jobFamilies,
        List<@Size(max = 100) String> jobLevels,
        List<@Size(max = 150) String> jobTitles
) {
}
