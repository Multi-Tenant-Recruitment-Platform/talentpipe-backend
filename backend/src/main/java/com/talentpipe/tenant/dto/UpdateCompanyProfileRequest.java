package com.talentpipe.tenant.dto;

import com.talentpipe.common.util.MaxCurrentYear;
import com.talentpipe.common.util.ValidPhone;
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

        // ---- taxonomy lists (null treated as empty list by service layer)
        List<String> values,
        List<String> benefits,
        List<String> workModes,
        List<String> officeLocations,
        List<String> departments,
        List<String> teams,
        List<String> businessUnits,
        List<String> employmentTypes,
        List<String> jobCategories,
        List<String> jobFamilies,
        List<String> jobLevels,
        List<String> jobTitles
) {
}
