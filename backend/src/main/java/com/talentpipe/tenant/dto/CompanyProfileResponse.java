package com.talentpipe.tenant.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full company profile response — the shape returned by {@code GET /api/v1/tenant}
 * and {@code PATCH /api/v1/tenant}. Matches the frontend's {@code CompanyProfileResponse}
 * type in {@code types.ts} exactly (camelCase, all 50+ fields).
 *
 * <p>This record is intentionally separate from {@link TenantResponse} to avoid
 * breaking the existing auth-module contract. Auth-module callers keep using the
 * lean {@code TenantResponse}; dashboard-settings callers use this richer DTO.</p>
 *
 * <p>Array fields default to empty list, never null, so the frontend never needs
 * a null-guard on iteration.</p>
 */
public record CompanyProfileResponse(
        // ---- identity (read-only; never modifiable by PATCH)
        UUID id,
        String name,
        String subdomain,
        String planTier,
        String status,

        // ---- branding
        String logoUrl,
        String coverImageUrl,
        String tagline,

        // ---- company info
        String industry,
        String companyType,
        String size,
        Integer employeeCount,
        Integer foundedYear,
        String description,
        String culture,
        String mission,
        String vision,
        String legalName,
        String registrationNumber,

        // ---- localisation
        String timezone,
        String currency,
        String language,

        // ---- contact
        String email,
        String hrEmail,
        String phone,
        String alternativePhone,
        String website,
        String linkedinUrl,
        String facebookUrl,
        String twitterUrl,
        String instagramUrl,

        // ---- location
        String address,
        String city,
        String state,
        String postalCode,
        String country,

        // ---- taxonomy lists (never null — empty list when not set)
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
        List<String> jobTitles,

        // ---- audit
        Instant updatedAt
) {
}
