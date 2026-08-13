package com.talentpipe.tenant.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The company's own profile as the settings screen needs it (PB-005).
 *
 * <p>Distinct from {@link TenantResponse}, which stays the small identity
 * projection other modules resolve a tenant through. This is the wide,
 * screen-shaped view and is returned only by the tenant controller.</p>
 *
 * <p>{@code logoUrl} and {@code coverImageUrl} are URLs, not bytes: an
 * {@code <img>} tag cannot attach an Authorization header, so the images are
 * served from the public endpoint and referenced here. They carry a {@code v}
 * query parameter derived from {@code updatedAt} so a replaced image is not
 * masked by a cached copy. Null when no image has been uploaded.</p>
 */
public record CompanyProfileResponse(

        UUID id,
        String name,
        /** Workspace address. Identity, not a profile field — never editable. */
        String subdomain,

        String logoUrl,
        String coverImageUrl,

        String tagline,
        String industry,
        String companyType,
        String size,
        Integer employeeCount,
        Integer foundedYear,

        String description,
        String culture,
        String mission,
        String vision,
        List<String> values,
        List<String> benefits,

        String legalName,
        String registrationNumber,
        List<String> workModes,
        String timezone,
        String currency,
        String language,

        String email,
        String hrEmail,
        String phone,
        String alternativePhone,
        String website,
        String linkedinUrl,
        String facebookUrl,
        String twitterUrl,
        String instagramUrl,

        String address,
        String city,
        String state,
        String postalCode,
        String country,
        List<String> officeLocations,

        List<String> departments,
        List<String> teams,
        List<String> businessUnits,
        List<String> employmentTypes,
        List<String> jobCategories,
        List<String> jobFamilies,
        List<String> jobLevels,
        List<String> jobTitles,

        /** Billing/lifecycle state. Read-only here; no endpoint changes them. */
        String planTier,
        String status,
        Instant updatedAt
) {
}
