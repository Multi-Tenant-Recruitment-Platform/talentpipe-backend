package com.talentpipe.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.candidate.entity.Candidate;
import com.talentpipe.candidate.entity.CandidateCv;
import com.talentpipe.candidate.repository.CandidateCvRepository;
import com.talentpipe.candidate.validator.CandidateCvValidator;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.common.storage.FileStorageService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class CandidateCvServiceTest {

    @Mock
    private CandidateCvRepository cvRepository;

    @Mock
    private CandidateCvValidator cvValidator;

    @Mock
    private FileStorageService storageService;

    @Mock
    private TalentPoolAuditService auditService;

    @InjectMocks
    private CandidateCvService cvService;

    private Candidate candidate;

    @BeforeEach
    void setUp() {
        candidate = new Candidate(
                "candidate@example.com",
                "hash",
                "Kasun Perera",
                "200012345678",
                "Colombo",
                "+94 77 123 4567",
                UserStatus.ACTIVE);
    }

    @Test
    void uploadCvArchivesPreviousAndIncrementsVersion() {
        CandidateCv previousCv = new CandidateCv(
                candidate.getId(),
                "old.pdf",
                "candidate/old.pdf",
                "/download",
                "application/pdf",
                1000L,
                "differenthash",
                1);

        when(cvRepository.findFirstByCandidateIdAndIsActiveTrue(candidate.getId()))
                .thenReturn(Optional.of(previousCv));

        byte[] newBytes = new byte[]{0x25, 0x50, 0x44, 0x46, 1, 2, 3};
        MockMultipartFile newFile = new MockMultipartFile(
                "cv",
                "new_resume.pdf",
                "application/pdf",
                newBytes);

        when(storageService.store(eq("candidate"), eq(candidate.getId()), eq("cvs"), eq("new_resume.pdf"), any(), any()))
                .thenReturn("candidate/new_resume.pdf");

        when(cvRepository.save(any(CandidateCv.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CandidateCv uploaded = cvService.uploadCv(candidate, newFile);

        assertThat(uploaded.getVersion()).isEqualTo(2);
        assertThat(uploaded.isActive()).isTrue();
        assertThat(previousCv.isActive()).isFalse();
        assertThat(previousCv.getArchivedAt()).isNotNull();

        verify(auditService).recordEvent(eq(candidate.getId()), eq("UPDATED_CV"), eq(true), any());
    }

    @Test
    void uploadCvIsIdempotentWhenFileContentIdentical() throws Exception {
        byte[] bytes = new byte[]{0x25, 0x50, 0x44, 0x46, 1, 2, 3};
        MockMultipartFile file = new MockMultipartFile("cv", "resume.pdf", "application/pdf", bytes);

        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        String expectedHash = java.util.HexFormat.of().formatHex(digest.digest(bytes));

        CandidateCv activeCv = new CandidateCv(
                candidate.getId(),
                "resume.pdf",
                "key",
                "url",
                "application/pdf",
                bytes.length,
                expectedHash,
                1);

        when(cvRepository.findFirstByCandidateIdAndIsActiveTrue(candidate.getId()))
                .thenReturn(Optional.of(activeCv));

        CandidateCv result = cvService.uploadCv(candidate, file);

        assertThat(result).isSameAs(activeCv);
        verify(storageService, never()).store(any(), any(), any(), any(), any(), any());
        verify(auditService, never()).recordEvent(any(), any(), any(Boolean.class), any());
    }

    @Test
    void removeActiveCvArchivesCvAndAudits() {
        CandidateCv activeCv = new CandidateCv(
                candidate.getId(),
                "resume.pdf",
                "key",
                "url",
                "application/pdf",
                100L,
                "hash",
                1);

        when(cvRepository.findFirstByCandidateIdAndIsActiveTrue(candidate.getId()))
                .thenReturn(Optional.of(activeCv));

        cvService.removeActiveCv(candidate);

        assertThat(activeCv.isActive()).isFalse();
        verify(cvRepository).save(activeCv);
        verify(auditService).recordEvent(candidate.getId(), "REMOVED_CV", true, null);
    }

    @Test
    void deactivateForLeavingPoolArchivesCvAndAudits() {
        CandidateCv activeCv = new CandidateCv(
                candidate.getId(),
                "resume.pdf",
                "key",
                "url",
                "application/pdf",
                100L,
                "hash",
                1);

        when(cvRepository.findFirstByCandidateIdAndIsActiveTrue(candidate.getId()))
                .thenReturn(Optional.of(activeCv));

        cvService.deactivateForLeavingPool(candidate);

        assertThat(activeCv.isActive()).isFalse();
        verify(cvRepository).save(activeCv);
        verify(auditService).recordEvent(candidate.getId(), "LEFT_POOL", false, null);
    }

    @Test
    void loadActiveCvResourceThrowsWhenNoActiveCv() {
        when(cvRepository.findFirstByCandidateIdAndIsActiveTrue(candidate.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cvService.loadActiveCvResource(candidate.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No active CV found");
    }

    @Test
    void uploadCvFirstTimeUploadCreatesVersion1() {
        when(cvRepository.findFirstByCandidateIdAndIsActiveTrue(candidate.getId()))
                .thenReturn(Optional.empty());

        byte[] newBytes = new byte[]{0x25, 0x50, 0x44, 0x46, 9, 8, 7};
        MockMultipartFile newFile = new MockMultipartFile(
                "cv",
                "first_resume.pdf",
                "application/pdf",
                newBytes);

        when(storageService.store(eq("candidate"), eq(candidate.getId()), eq("cvs"), eq("first_resume.pdf"), any(), any()))
                .thenReturn("candidate/" + candidate.getId() + "/cvs/uuid.pdf");

        when(cvRepository.save(any(CandidateCv.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CandidateCv uploaded = cvService.uploadCv(candidate, newFile);

        assertThat(uploaded.getVersion()).isEqualTo(1);
        assertThat(uploaded.isActive()).isTrue();
        assertThat(uploaded.getFileName()).isEqualTo("first_resume.pdf");

        verify(auditService).recordEvent(eq(candidate.getId()), eq("UPDATED_CV"), eq(true), any());
    }

    @Test
    void uploadCvThrowsWhenFileBytesFail() throws Exception {
        org.springframework.web.multipart.MultipartFile mockFile = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(mockFile.getBytes()).thenThrow(new java.io.IOException("Disk read failure"));

        assertThatThrownBy(() -> cvService.uploadCv(candidate, mockFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to read CV file bytes");
    }

    @Test
    void removeActiveCvNoOpWhenNoActiveCv() {
        when(cvRepository.findFirstByCandidateIdAndIsActiveTrue(candidate.getId()))
                .thenReturn(Optional.empty());

        cvService.removeActiveCv(candidate);

        verify(cvRepository, never()).save(any());
        verify(auditService, never()).recordEvent(any(), any(), any(Boolean.class), any());
    }

    @Test
    void deactivateForLeavingPoolWhenNoActiveCvAuditsLeftPoolOnly() {
        when(cvRepository.findFirstByCandidateIdAndIsActiveTrue(candidate.getId()))
                .thenReturn(Optional.empty());

        cvService.deactivateForLeavingPool(candidate);

        verify(cvRepository, never()).save(any());
        verify(auditService).recordEvent(candidate.getId(), "LEFT_POOL", false, null);
    }

    @Test
    void loadActiveCvResourceSuccessReturnsPayload() {
        CandidateCv activeCv = new CandidateCv(
                candidate.getId(),
                "my_cv.pdf",
                "candidate/key/my_cv.pdf",
                "/download",
                "application/pdf",
                500L,
                "sha256",
                1);

        when(cvRepository.findFirstByCandidateIdAndIsActiveTrue(candidate.getId()))
                .thenReturn(Optional.of(activeCv));
        byte[] expectedBytes = new byte[]{1, 2, 3, 4};
        when(storageService.load("candidate/key/my_cv.pdf")).thenReturn(expectedBytes);

        CandidateCvService.CvDownloadPayload payload = cvService.loadActiveCvResource(candidate.getId());

        assertThat(payload.fileName()).isEqualTo("my_cv.pdf");
        assertThat(payload.mimeType()).isEqualTo("application/pdf");
        assertThat(payload.data()).isEqualTo(expectedBytes);
    }

    @Test
    void uploadCvCleansUpStoredFileWhenRepositorySaveFails() {
        byte[] newBytes = new byte[]{0x25, 0x50, 0x44, 0x46, 1, 2, 3};
        MockMultipartFile newFile = new MockMultipartFile(
                "cv",
                "resume.pdf",
                "application/pdf",
                newBytes);

        String storedKey = "candidate/" + candidate.getId() + "/cvs/resume.pdf";
        when(storageService.store(eq("candidate"), eq(candidate.getId()), eq("cvs"), eq("resume.pdf"), any(), any()))
                .thenReturn(storedKey);

        when(cvRepository.save(any(CandidateCv.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Unique constraint violation"));

        assertThatThrownBy(() -> cvService.uploadCv(candidate, newFile))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        verify(storageService).delete(storedKey);
    }
}
