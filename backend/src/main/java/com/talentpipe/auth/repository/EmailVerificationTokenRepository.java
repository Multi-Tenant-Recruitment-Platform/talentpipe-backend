package com.talentpipe.auth.repository;

import com.talentpipe.auth.entity.EmailVerificationToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Persistence access for email verification tokens — private to the auth module. */
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    /** Token lookup by hash (the raw token is never stored). */
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Removes all outstanding tokens for a user before issuing a fresh one,
     * so at most one valid token exists per user at any time.
     */
    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.userId = :userId")
    void deleteAllByUserId(UUID userId);
}
