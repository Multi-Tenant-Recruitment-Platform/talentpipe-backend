package com.talentpipe.candidate.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.auth.entity.UserStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateEntityTest {

    @Test
    void candidateProfileFieldsAndUpdaters() {
        Candidate candidate = new Candidate(
                "test@example.com",
                "hash",
                "Test User",
                "199012345678",
                "Kandy",
                "+94 71 123 4567",
                UserStatus.ACTIVE);

        candidate.setPhotoUrl("http://cdn.example.com/photo.jpg");
        candidate.setInTalentPool(true);
        candidate.setFullName("Updated User");
        candidate.setEmail("updated@example.com");
        candidate.setIdentityCardNumber("199012345679");
        candidate.setContactNumber("+94 71 999 9999");

        assertThat(candidate.getPhotoUrl()).isEqualTo("http://cdn.example.com/photo.jpg");
        assertThat(candidate.isInTalentPool()).isTrue();
        assertThat(candidate.getFullName()).isEqualTo("Updated User");
        assertThat(candidate.getEmail()).isEqualTo("updated@example.com");
        assertThat(candidate.getIdentityCardNumber()).isEqualTo("199012345679");
        assertThat(candidate.getContactNumber()).isEqualTo("+94 71 999 9999");

        candidate.updateProfile("Final User", "final@example.com", "200012345678", "+94 77 111 2222", false);
        assertThat(candidate.getFullName()).isEqualTo("Final User");
        assertThat(candidate.getEmail()).isEqualTo("final@example.com");
        assertThat(candidate.isInTalentPool()).isFalse();
    }

    @Test
    void candidateCvFieldsAndArchive() {
        UUID candidateId = UUID.randomUUID();
        CandidateCv cv = new CandidateCv(
                candidateId,
                "resume.pdf",
                "candidate/" + candidateId + "/cvs/file.pdf",
                "application/pdf",
                2048L,
                "sha256hash",
                1);

        assertThat(cv.getCandidateId()).isEqualTo(candidateId);
        assertThat(cv.getFileName()).isEqualTo("resume.pdf");
        assertThat(cv.getStorageKey()).contains("file.pdf");
        assertThat(cv.getStorageUrl()).isEqualTo(CandidateCv.DEFAULT_STORAGE_URL);
        assertThat(cv.getMimeType()).isEqualTo("application/pdf");
        assertThat(cv.getFileSizeBytes()).isEqualTo(2048L);
        assertThat(cv.getFileHashSha256()).isEqualTo("sha256hash");
        assertThat(cv.getVersion()).isEqualTo(1);
        assertThat(cv.isActive()).isTrue();
        assertThat(cv.getUploadedAt()).isNotNull();
        assertThat(cv.getArchivedAt()).isNull();

        cv.setStorageUrl("/new-download");
        assertThat(cv.getStorageUrl()).isEqualTo("/new-download");

        Instant now = Instant.now();
        cv.archive(now);
        assertThat(cv.isActive()).isFalse();
        assertThat(cv.getArchivedAt()).isEqualTo(now);
    }

    @Test
    void candidateTalentPoolAuditFields() {
        UUID candidateId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();
        CandidateTalentPoolAudit audit = new CandidateTalentPoolAudit(candidateId, "JOINED_POOL", true, cvId);

        assertThat(audit.getCandidateId()).isEqualTo(candidateId);
        assertThat(audit.getEventType()).isEqualTo("JOINED_POOL");
        assertThat(audit.isJoinedPool()).isTrue();
        assertThat(audit.getCvId()).isEqualTo(cvId);
        assertThat(audit.getOccurredAt()).isNotNull();
    }

    @Test
    void candidateLockoutPolicyAndAuditHelpers() {
        Candidate candidate = new Candidate(
                "candidate@test.com",
                "hash",
                "Name",
                "199012345678",
                "Colombo",
                "+94 77 123 4567",
                UserStatus.ACTIVE);

        Instant now = Instant.now();
        assertThat(candidate.isLocked(now)).isFalse();
        assertThat(candidate.getFailedLoginCount()).isEqualTo((short) 0);
        assertThat(candidate.getLockedUntil()).isNull();

        // 4 failed logins shouldn't lock (limit is 5)
        for (int i = 0; i < 4; i++) {
            boolean locked = candidate.registerFailedLogin(now);
            assertThat(locked).isFalse();
        }
        assertThat(candidate.isLocked(now)).isFalse();

        // 5th failed login locks account
        boolean locked = candidate.registerFailedLogin(now);
        assertThat(locked).isTrue();
        assertThat(candidate.isLocked(now)).isTrue();
        assertThat(candidate.getLockedUntil()).isAfter(now);

        // Successful login clears lockout
        candidate.registerSuccessfulLogin();
        assertThat(candidate.isLocked(now)).isFalse();
        assertThat(candidate.getFailedLoginCount()).isEqualTo((short) 0);
        assertThat(candidate.getLockedUntil()).isNull();

        // Check passwordHash, address, toString
        candidate.setPasswordHash("newHash");
        assertThat(candidate.getPasswordHash()).isEqualTo("newHash");
        assertThat(candidate.getAddress()).isEqualTo("Colombo");
        assertThat(candidate.toString()).contains("Candidate{");
    }

    @Test
    void candidateNoArgConstructorsForJpa() {
        CandidateCv cv = new CandidateCv();
        assertThat(cv.getId()).isNull();

        CandidateTalentPoolAudit audit = new CandidateTalentPoolAudit();
        assertThat(audit.getId()).isNull();
    }
}
