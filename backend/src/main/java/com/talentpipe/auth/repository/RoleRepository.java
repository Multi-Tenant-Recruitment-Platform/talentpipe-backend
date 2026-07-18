package com.talentpipe.auth.repository;

import com.talentpipe.auth.entity.Role;
import com.talentpipe.auth.entity.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read-only access to the fixed role vocabulary (seeded by Flyway V3). */
public interface RoleRepository extends JpaRepository<Role, Short> {

    Optional<Role> findByName(RoleName name);
}
