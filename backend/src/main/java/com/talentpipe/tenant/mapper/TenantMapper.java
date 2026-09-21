package com.talentpipe.tenant.mapper;

import com.talentpipe.tenant.dto.CompanyProfileResponse;
import com.talentpipe.tenant.dto.PublicCompanyProfileResponse;
import com.talentpipe.tenant.dto.TenantResponse;
import com.talentpipe.tenant.entity.Tenant;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/** Entity ↔ DTO mapping for the tenant module. Entities never leave the module raw. */
@Component
public class TenantMapper {

    /**
     * Maps to the lean response used by the auth module (register, login,
     * subdomain-resolution). Kept unchanged so no auth-module callers break.
     */
    public TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSubdomain(),
                tenant.getIndustry(),
                tenant.getPlanTier().name(),
                tenant.getStatus().name(),
                tenant.getCreatedAt());
    }

    /**
     * Maps to the full profile response used by the settings and dashboard
     * endpoints ({@code GET /api/v1/tenant}, {@code PATCH /api/v1/tenant}).
     * List fields default to an empty unmodifiable list rather than null so
     * the frontend never needs a null-guard on iteration.
     */
    public CompanyProfileResponse toProfileResponse(Tenant tenant) {
        return new CompanyProfileResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSubdomain(),
                tenant.getPlanTier().name(),
                tenant.getStatus().name(),

                tenant.getLogoUrl(),
                tenant.getCoverImageUrl(),
                tenant.getTagline(),

                tenant.getIndustry(),
                tenant.getCompanyType(),
                tenant.getSize(),
                tenant.getFoundedYear(),
                tenant.getDescription(),
                tenant.getMission(),
                tenant.getVision(),
                tenant.getLegalName(),
                tenant.getRegistrationNumber(),

                tenant.getTimezone(),
                tenant.getCurrency(),
                tenant.getLanguage(),

                tenant.getEmail(),
                tenant.getHrEmail(),
                tenant.getPhone(),
                tenant.getAlternativePhone(),
                tenant.getWebsite(),
                tenant.getLinkedinUrl(),
                tenant.getFacebookUrl(),
                tenant.getTwitterUrl(),
                tenant.getInstagramUrl(),

                tenant.getCity(),
                tenant.getState(),
                tenant.getPostalCode(),
                tenant.getCountry(),

                safeList(tenant.getBenefits()),
                safeList(tenant.getOfficeLocations()),
                safeList(tenant.getDepartments()),

                tenant.getUpdatedAt());
    }

    /**
     * Maps to the curated public response for
     * {@code GET /api/v1/public/companies/{subdomain}}.
     * Excludes billing, legal, internal contact, and taxonomy data.
     */
    public PublicCompanyProfileResponse toPublicResponse(Tenant tenant) {
        return new PublicCompanyProfileResponse(
                tenant.getName(),
                tenant.getSubdomain(),
                tenant.getLogoUrl(),
                tenant.getCoverImageUrl(),
                tenant.getTagline(),
                tenant.getDescription(),
                tenant.getIndustry(),
                tenant.getSize(),
                tenant.getFoundedYear(),
                tenant.getWebsite(),
                tenant.getLinkedinUrl(),
                tenant.getTwitterUrl(),
                tenant.getInstagramUrl(),
                tenant.getFacebookUrl(),
                tenant.getCity(),
                tenant.getCountry());
    }

    /** Returns the list as-is if non-null, otherwise an empty unmodifiable list. */
    private static List<String> safeList(List<String> list) {
        return list != null ? list : Collections.emptyList();
    }
}
