package com.talentpipe.candidate.service;

import com.talentpipe.candidate.entity.CandidateTalentPoolAudit;
import com.talentpipe.candidate.repository.CandidateTalentPoolAuditRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit recording service for Talent Pool activity.
 */
@Service
public class TalentPoolAuditService {

    private static final Logger log = LoggerFactory.getLogger(TalentPoolAuditService.class);

    private final CandidateTalentPoolAuditRepository auditRepository;

    public TalentPoolAuditService(CandidateTalentPoolAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * Records a Talent Pool event in an audit log.
     *
     * @param candidateId candidate experiencing the event
     * @param eventType   one of 'JOINED_POOL', 'LEFT_POOL', 'UPDATED_CV', 'REMOVED_CV'
     * @param joinedPool  whether the candidate is in the pool after the event
     * @param cvId        associated CV ID, or null
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordEvent(UUID candidateId, String eventType, boolean joinedPool, UUID cvId) {
        CandidateTalentPoolAudit audit = new CandidateTalentPoolAudit(candidateId, eventType, joinedPool, cvId);
        auditRepository.save(audit);
        log.info("Recorded talent pool audit event [{}] for candidate {}", eventType, candidateId);
    }
}
