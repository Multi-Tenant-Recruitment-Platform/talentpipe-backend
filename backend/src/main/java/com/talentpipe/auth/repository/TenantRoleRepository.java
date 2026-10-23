package com.talentpipe.auth.repository;

import com.talentpipe.auth.entity.SystemRole;
import com.talentpipe.auth.entity.TenantRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for company roles — private to the auth module.
 *
 * <p>Every lookup takes the tenant id from the caller's token as part of its
 * key, so another company's role is simply not found (404).</p>
 */
public interface TenantRoleRepository extends JpaRepository<TenantRole, UUID> {

    Optional<TenantRole> findByIdAndTenantId(UUID id, UUID tenantId);

    /** The workspace's roles, unordered — callers sort for display. */
    List<TenantRole> findAllByTenantId(UUID tenantId);

    Optional<TenantRole> findByTenantIdAndSystemRole(UUID tenantId, SystemRole systemRole);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(UUID tenantId, String name, UUID id);

    long countByTenantIdAndSystemRoleIsNull(UUID tenantId);
}
