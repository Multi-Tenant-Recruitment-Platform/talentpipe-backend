package com.talentpipe.tenant.service;

import com.talentpipe.common.exception.DuplicateResourceException;
import com.talentpipe.tenant.dto.TenantResponse;
import com.talentpipe.tenant.entity.Tenant;
import com.talentpipe.tenant.mapper.TenantMapper;
import com.talentpipe.tenant.repository.TenantRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tenant module's public API. Other modules (auth, and later job,
 * pipeline, …) interact with tenants exclusively through this service and its
 * DTOs — the {@code Tenant} entity never crosses the module boundary.
 */
@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;

    public TenantService(TenantRepository tenantRepository, TenantMapper tenantMapper) {
        this.tenantRepository = tenantRepository;
        this.tenantMapper = tenantMapper;
    }

    /**
     * Creates a tenant during company onboarding. Participates in the
     * caller's transaction so tenant + first admin are created atomically.
     *
     * @throws DuplicateResourceException when the subdomain is already taken (409)
     */
    @Transactional
    public TenantResponse createTenant(String name, String subdomain) {
        String normalizedSubdomain = normalize(subdomain);
        if (tenantRepository.existsBySubdomain(normalizedSubdomain)) {
            throw new DuplicateResourceException(
                    "Subdomain '" + normalizedSubdomain + "' is already taken");
        }
        Tenant tenant = tenantRepository.save(new Tenant(name.trim(), normalizedSubdomain));
        return tenantMapper.toResponse(tenant);
    }

    /** Resolves a tenant by subdomain (used for login tenant resolution). */
    @Transactional(readOnly = true)
    public Optional<TenantResponse> findBySubdomain(String subdomain) {
        return tenantRepository.findBySubdomain(normalize(subdomain))
                .map(tenantMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<TenantResponse> findById(UUID tenantId) {
        return tenantRepository.findById(tenantId).map(tenantMapper::toResponse);
    }

    /** Subdomains are case-insensitive identifiers: store and compare lowercase. */
    private String normalize(String subdomain) {
        return subdomain == null ? null : subdomain.trim().toLowerCase(Locale.ROOT);
    }
}
