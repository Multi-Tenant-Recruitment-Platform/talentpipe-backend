package com.talentpipe.auth.repository;

import com.talentpipe.auth.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Persistence access for refresh tokens — lookups always go through the hash. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Bulk-revokes all non-revoked refresh tokens for a user. Called after a
     * successful password reset so every existing session is immediately
     * invalidated. Uses a JPQL update to avoid loading entities into memory.
     */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now " +
           "WHERE t.userId = :userId AND t.revokedAt IS NULL")
    void revokeAllActiveForUser(UUID userId, Instant now);
}
