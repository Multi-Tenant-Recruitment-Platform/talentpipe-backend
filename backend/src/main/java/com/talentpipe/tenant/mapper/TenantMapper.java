package com.talentpipe.tenant.mapper;

import com.talentpipe.tenant.dto.TenantResponse;
import com.talentpipe.tenant.entity.Tenant;
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
}
