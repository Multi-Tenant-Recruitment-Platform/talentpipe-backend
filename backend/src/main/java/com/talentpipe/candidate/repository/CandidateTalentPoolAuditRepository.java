package com.talentpipe.candidate.repository;

import com.talentpipe.candidate.entity.CandidateTalentPoolAudit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence operations for Talent Pool audit trails.
 */
public interface CandidateTalentPoolAuditRepository extends JpaRepository<CandidateTalentPoolAudit, UUID> {

    /** Returns the candidate's audit log in reverse chronological order. */
    List<CandidateTalentPoolAudit> findByCandidateIdOrderByOccurredAtDesc(UUID candidateId);
}
