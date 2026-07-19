package com.talentpipe.auth.repository;

import com.talentpipe.auth.entity.PasswordResetToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Persistence access for password reset tokens — private to the auth module. */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /** Token lookup by hash (the raw token is never stored). */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Removes all outstanding tokens for a user before issuing a fresh one,
     * preventing a user from having multiple valid reset tokens simultaneously.
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.userId = :userId")
    void deleteAllByUserId(UUID userId);
}
