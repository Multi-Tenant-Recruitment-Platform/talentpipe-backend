package com.talentpipe.application.repository;

import com.talentpipe.application.entity.Application;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    boolean existsByCandidateIdAndJobId(UUID candidateId, UUID jobId);

    Optional<Application> findByIdAndCandidateId(UUID id, UUID candidateId);

    List<Application> findAllByCandidateIdOrderByCreatedAtDesc(UUID candidateId);

    List<Application> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
