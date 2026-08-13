package com.talentpipe.tenant.mapper;

import com.talentpipe.tenant.dto.CompanyProfileResponse;
import com.talentpipe.tenant.dto.TenantResponse;
import com.talentpipe.tenant.entity.Tenant;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Entity ↔ DTO mapping for the tenant module. Entities never leave the module raw. */
@Component
public class TenantMapper {

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

    /** The wide, settings-screen view. Image bytes stay behind URLs. */
    public CompanyProfileResponse toProfileResponse(Tenant tenant) {
        return new CompanyProfileResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSubdomain(),
                imageUrl(tenant, "logo", tenant.getLogoContentType() != null),
                imageUrl(tenant, "cover", tenant.getCoverContentType() != null),
                tenant.getTagline(),
                tenant.getIndustry(),
                tenant.getCompanyType(),
                tenant.getSize(),
                tenant.getEmployeeCount(),
                tenant.getFoundedYear(),
                tenant.getDescription(),
                tenant.getCulture(),
                tenant.getMission(),
                tenant.getVision(),
                tenant.getValues(),
                tenant.getBenefits(),
                tenant.getLegalName(),
                tenant.getRegistrationNumber(),
                tenant.getWorkModes(),
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
                tenant.getAddress(),
                tenant.getCity(),
                tenant.getState(),
                tenant.getPostalCode(),
                tenant.getCountry(),
                tenant.getOfficeLocations(),
                tenant.getDepartments(),
                tenant.getTeams(),
                tenant.getBusinessUnits(),
                tenant.getEmploymentTypes(),
                tenant.getJobCategories(),
                tenant.getJobFamilies(),
                tenant.getJobLevels(),
                tenant.getJobTitles(),
                tenant.getPlanTier().name(),
                tenant.getStatus().name(),
                tenant.getUpdatedAt());
    }

    /**
     * Public URL for a stored image, or null when none is stored.
     *
     * <p>Relative on purpose: the SPA is served same-origin with the API (the
     * Vite proxy in development, nginx in Docker), so a relative path works in
     * every environment and hardcodes no host into stored data.</p>
     *
     * <p>The {@code v} parameter busts the browser cache when an image is
     * replaced — without it, a new upload keeps showing the old picture for as
     * long as the cached copy lives.</p>
     */
    private String imageUrl(Tenant tenant, String kind, boolean present) {
        if (!present) {
            return null;
        }
        Instant updatedAt = tenant.getUpdatedAt();
        long version = updatedAt == null ? 0L : updatedAt.toEpochMilli();
        return "/api/v1/public/tenants/" + tenant.getId() + "/" + kind + "?v=" + version;
    }
}
