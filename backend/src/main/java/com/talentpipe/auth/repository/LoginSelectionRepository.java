package com.talentpipe.auth.repository;

import com.talentpipe.auth.entity.LoginSelection;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LoginSelectionRepository extends JpaRepository<LoginSelection, UUID> {

    /**
     * Token lookup by hash, locking the row until the transaction ends, so two
     * requests racing with the same token cannot both sign in.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LoginSelection> findByTokenHash(String tokenHash);

    /** Removes choices that can no longer be made; their account rows cascade. */
    @Modifying
    @Query("DELETE FROM LoginSelection s WHERE s.expiresAt < :cutoff")
    int deleteExpiredBefore(Instant cutoff);
}
