package com.talentpipe.auth.repository;

import com.talentpipe.auth.entity.RoleAuditEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for the access audit trail — append-only by convention. */
public interface RoleAuditEventRepository extends JpaRepository<RoleAuditEvent, UUID> {

    Page<RoleAuditEvent> findAllByTenantIdOrderByOccurredAtDesc(UUID tenantId, Pageable pageable);
}
