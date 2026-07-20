package com.talentpipe.auth.repository;

import com.talentpipe.auth.entity.User;
import java.util.List;
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

    /**
     * SUPER_ADMIN lookup. Platform operators have {@code tenant_id IS NULL},
     * and SQL equality never matches NULL — passing {@code null} to
     * {@link #findByTenantIdAndEmail} silently returns nothing, so the null
     * case needs this dedicated {@code IS NULL} query.
     */
    Optional<User> findByTenantIdIsNullAndEmail(String email);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    /** Global email lookup used by the tenant-agnostic forgot-password flow. */
    List<User> findAllByEmail(String email);

    /** Team listing for a tenant (PB-003/PB-004), newest members last. */
    List<User> findAllByTenantIdOrderByCreatedAtAsc(UUID tenantId);
}
