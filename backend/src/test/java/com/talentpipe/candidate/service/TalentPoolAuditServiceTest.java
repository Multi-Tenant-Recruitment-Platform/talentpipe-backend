package com.talentpipe.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.talentpipe.candidate.entity.CandidateTalentPoolAudit;
import com.talentpipe.candidate.repository.CandidateTalentPoolAuditRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TalentPoolAuditServiceTest {

    @Mock
    private CandidateTalentPoolAuditRepository auditRepository;

    @InjectMocks
    private TalentPoolAuditService auditService;

    @Test
    void recordEventSavesAuditEntityWithCorrectFields() {
        UUID candidateId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();

        auditService.recordEvent(candidateId, "JOINED_POOL", true, cvId);

        ArgumentCaptor<CandidateTalentPoolAudit> captor = ArgumentCaptor.forClass(CandidateTalentPoolAudit.class);
        verify(auditRepository).save(captor.capture());

        CandidateTalentPoolAudit audit = captor.getValue();
        assertThat(audit.getCandidateId()).isEqualTo(candidateId);
        assertThat(audit.getEventType()).isEqualTo("JOINED_POOL");
        assertThat(audit.isJoinedPool()).isTrue();
        assertThat(audit.getCvId()).isEqualTo(cvId);
        assertThat(audit.getOccurredAt()).isNotNull();
    }
}
