package com.talentpipe.auth.repository;

import com.talentpipe.auth.entity.InvitationToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Persistence access for invitation tokens — private to the auth module. */
public interface InvitationTokenRepository extends JpaRepository<InvitationToken, UUID> {

    /** Token lookup by hash (the raw token is never stored). */
    Optional<InvitationToken> findByTokenHash(String tokenHash);

    /** Clears outstanding tokens before re-issuing, so only one is ever valid. */
    @Modifying
    @Query("DELETE FROM InvitationToken t WHERE t.userId = :userId")
    void deleteAllByUserId(UUID userId);
}
