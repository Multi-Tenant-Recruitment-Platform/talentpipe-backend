package com.talentpipe.candidate.repository;

import com.talentpipe.candidate.entity.CandidateCv;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence operations for candidate CVs.
 */
public interface CandidateCvRepository extends JpaRepository<CandidateCv, UUID> {

    /** Finds the candidate's currently active CV, if one exists. */
    Optional<CandidateCv> findFirstByCandidateIdAndIsActiveTrue(UUID candidateId);

    /** Finds all CVs for the candidate ordered by upload date. */
    List<CandidateCv> findByCandidateIdOrderByUploadedAtDesc(UUID candidateId);

    /** Counts all historical CV uploads for the candidate. */
    int countByCandidateId(UUID candidateId);
}
