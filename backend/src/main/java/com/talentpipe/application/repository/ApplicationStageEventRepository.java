package com.talentpipe.application.repository;

import com.talentpipe.application.entity.ApplicationStageEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Applications' histories. The ORM tenant filter scopes every lookup to the caller's company. */
public interface ApplicationStageEventRepository extends JpaRepository<ApplicationStageEvent, UUID> {

    List<ApplicationStageEvent> findAllByApplicationIdOrderByCreatedAtAscIdAsc(UUID applicationId);
}
