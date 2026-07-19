package com.talentpipe.candidate.repository;

import com.talentpipe.candidate.entity.CandidateVerificationToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Persistence access for candidate verification tokens. */
public interface CandidateVerificationTokenRepository extends JpaRepository<CandidateVerificationToken, UUID> {

    Optional<CandidateVerificationToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM CandidateVerificationToken t WHERE t.candidateId = :candidateId")
    void deleteAllByCandidateId(UUID candidateId);
}
