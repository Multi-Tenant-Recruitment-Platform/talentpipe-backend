package com.talentpipe.auth.repository;

import com.talentpipe.auth.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for users — private to the auth module by convention. */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Login lookup. Users are unique per (tenant_id, email) — the same email
     * may exist in several tenants, so the tenant must always be part of the
     * lookup key.
     */
    Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);
}
