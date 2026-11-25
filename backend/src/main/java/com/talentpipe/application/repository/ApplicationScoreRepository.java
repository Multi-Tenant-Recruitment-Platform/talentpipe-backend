package com.talentpipe.application.repository;

import com.talentpipe.application.entity.ApplicationScore;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The screening results of applications. The ORM tenant filter scopes every lookup to the caller's company. */
public interface ApplicationScoreRepository extends JpaRepository<ApplicationScore, UUID> {

    /** The result of an application's latest screening, if it has been screened. */
    Optional<ApplicationScore> findByApplicationId(UUID applicationId);
}
