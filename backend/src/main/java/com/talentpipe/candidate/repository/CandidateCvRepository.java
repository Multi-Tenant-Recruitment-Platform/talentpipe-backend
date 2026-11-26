package com.talentpipe.candidate.repository;

import com.talentpipe.candidate.entity.CandidateCv;
import com.talentpipe.candidate.entity.CvExtractionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence operations for candidate CVs.
 */
public interface CandidateCvRepository extends JpaRepository<CandidateCv, UUID> {

    /** Finds the candidate's currently active CV, if one exists. */
    Optional<CandidateCv> findFirstByCandidateIdAndIsActiveTrue(UUID candidateId);

    /** Finds all CVs for the candidate ordered by upload date. */
    List<CandidateCv> findByCandidateIdOrderByUploadedAtDesc(UUID candidateId);

    /** The id of the candidate's active CV, without loading the CV and its text. */
    @Query("select cv.id from CandidateCv cv where cv.candidateId = :candidateId and cv.isActive = true")
    Optional<UUID> findActiveCvId(@Param("candidateId") UUID candidateId);

    /** A CV with this exact file whose extraction has got to one of the given statuses. */
    Optional<CandidateCv> findFirstByFileHashSha256AndExtractionStatusIn(
            String fileHashSha256, Collection<CvExtractionStatus> statuses);

    /** Counts all historical CV uploads for the candidate. */
    int countByCandidateId(UUID candidateId);
}
