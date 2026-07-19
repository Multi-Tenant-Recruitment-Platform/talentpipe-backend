package com.talentpipe.candidate.repository;

import com.talentpipe.candidate.entity.Candidate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for candidates — private to the candidate module by convention. */
public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    Optional<Candidate> findByEmail(String email);

    boolean existsByEmail(String email);
}
