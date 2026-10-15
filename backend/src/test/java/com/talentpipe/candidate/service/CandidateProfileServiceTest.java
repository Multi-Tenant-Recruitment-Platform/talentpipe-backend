package com.talentpipe.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.candidate.dto.MyCandidateProfileResponse;
import com.talentpipe.candidate.dto.UpdateCandidateProfileRequest;
import com.talentpipe.candidate.entity.Candidate;
import com.talentpipe.candidate.entity.CandidateCv;
import com.talentpipe.candidate.repository.CandidateRepository;
import com.talentpipe.candidate.validator.CandidatePhotoValidator;
import com.talentpipe.common.exception.AccountNotVerifiedException;
import com.talentpipe.common.exception.DuplicateResourceException;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.common.storage.FileStorageService;
import com.talentpipe.common.util.HtmlSanitizer;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class CandidateProfileServiceTest {

    @Mock
    private CandidateRepository candidateRepository;

    @Mock
    private CandidateCvService cvService;

    @Mock
    private CandidatePhotoValidator photoValidator;

    @Mock
    private FileStorageService storageService;

    @Mock
    private TalentPoolAuditService auditService;

    @Mock
    private HtmlSanitizer htmlSanitizer;

    @InjectMocks
    private CandidateProfileService profileService;

    private Candidate candidate;
    private UUID candidateId;

    @BeforeEach
    void setUp() {
        candidate = new Candidate(
                "kasun@example.com",
                "hashedpassword",
                "Kasun Perera",
                "200012345678",
                "Colombo",
                "+94 77 123 4567",
                UserStatus.ACTIVE);
        candidateId = candidate.getId();
    }

    private UpdateCandidateProfileRequest createValidRequest() {
        UpdateCandidateProfileRequest request = new UpdateCandidateProfileRequest();
        request.setFullName("Kasun Perera");
        request.setEmail("kasun@example.com");
        request.setIdentityCardNumber("200012345678");
        return request;
    }

    @Test
    void getProfileReturnsExpectedResponse() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(cvService.getActiveCv(candidateId)).thenReturn(Optional.empty());

        MyCandidateProfileResponse response = profileService.getProfile(candidateId);

        assertThat(response.fullName()).isEqualTo("Kasun Perera");
        assertThat(response.email()).isEqualTo("kasun@example.com");
        assertThat(response.identityCardNumber()).isEqualTo("200012345678");
        assertThat(response.cv()).isNull();
    }

    @Test
    void getProfileRejectsUnverifiedCandidate() {
        candidate.setStatus(UserStatus.PENDING_VERIFICATION);
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> profileService.getProfile(candidateId))
                .isInstanceOf(AccountNotVerifiedException.class)
                .hasMessageContaining("Email is not verified yet");
    }

    @Test
    void getProfileThrowsWhenNotFound() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getProfile(candidateId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Candidate profile not found");
    }

    @Test
    void updateProfileRejectsDuplicateEmail() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("Kasun Perera")).thenReturn("Kasun Perera");
        when(candidateRepository.existsByEmail("other@example.com")).thenReturn(true);

        UpdateCandidateProfileRequest request = createValidRequest();
        request.setEmail("other@example.com");

        assertThatThrownBy(() -> profileService.updateProfile(candidateId, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already in use");
    }

    @ParameterizedTest
    @CsvSource({
            "12, Format Invalid",
            "'   ', ID number cannot be blank",
            "2000!@#$1234, Format Invalid",
            "729991524V, outside valid calendar day ranges"
    })
    void updateProfileRejectsInvalidNicValues(String invalidNic, String expectedMessage) {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("Kasun Perera")).thenReturn("Kasun Perera");

        UpdateCandidateProfileRequest request = createValidRequest();
        request.setIdentityCardNumber(invalidNic);

        assertThatThrownBy(() -> profileService.updateProfile(candidateId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining(expectedMessage);
    }

    @Test
    void updateProfileHandlesPhotoRemoval() {
        candidate.setPhotoUrl("/api/v1/public/media/candidate/" + candidateId + "/photos/photo.png");

        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("Kasun Perera")).thenReturn("Kasun Perera");

        UpdateCandidateProfileRequest request = createValidRequest();
        request.setRemovePhoto(true);

        profileService.updateProfile(candidateId, request);

        assertThat(candidate.getPhotoUrl()).isNull();
        verify(storageService).delete(any());
    }

    @Test
    void updateProfileUploadsPhotoWhenProvided() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("Kasun Perera")).thenReturn("Kasun Perera");

        MockMultipartFile photo = new MockMultipartFile("photo", "avatar.png", "image/png", new byte[]{1, 2, 3});
        UpdateCandidateProfileRequest request = createValidRequest();
        request.setPhoto(photo);

        when(storageService.store(eq("candidate"), eq(candidateId), eq("photos"), eq("avatar.png"), any(), any()))
                .thenReturn("/api/v1/public/media/candidate/" + candidateId + "/photos/avatar.png");

        profileService.updateProfile(candidateId, request);

        verify(photoValidator).validate(photo);
        assertThat(candidate.getPhotoUrl()).contains("avatar.png");
    }

    @Test
    void updateProfileDeactivatesCvWhenLeavingTalentPool() {
        candidate.setInTalentPool(true);
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("Kasun Perera")).thenReturn("Kasun Perera");

        UpdateCandidateProfileRequest request = createValidRequest();
        request.setAddToTalentPool(false);

        profileService.updateProfile(candidateId, request);

        assertThat(candidate.isInTalentPool()).isFalse();
        verify(cvService).deactivateForLeavingPool(candidate);
    }

    @Test
    void getProfileReturnsCvResponseWhenActiveCvPresent() {
        CandidateCv cv = new CandidateCv(
                candidateId,
                "my_cv.pdf",
                "key",
                "application/pdf",
                5000L,
                "hash",
                1);
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(cvService.getActiveCv(candidateId)).thenReturn(Optional.of(cv));

        MyCandidateProfileResponse response = profileService.getProfile(candidateId);

        assertThat(response.cv()).isNotNull();
        assertThat(response.cv().fileName()).isEqualTo("my_cv.pdf");
        assertThat(response.cv().sizeBytes()).isEqualTo(5000L);
    }

    @Test
    void updateProfileRejectsBlankFullName() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        UpdateCandidateProfileRequest request = new UpdateCandidateProfileRequest();
        request.setFullName("   ");

        assertThatThrownBy(() -> profileService.updateProfile(candidateId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Full name cannot be blank");
    }

    @Test
    void updateProfileRejectsFullNameWithOnlyHtml() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("<b></b>")).thenReturn("");

        UpdateCandidateProfileRequest request = new UpdateCandidateProfileRequest();
        request.setFullName("<b></b>");

        assertThatThrownBy(() -> profileService.updateProfile(candidateId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Full name must contain valid text");
    }

    @Test
    void updateProfileRejectsBlankEmail() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("Kasun Perera")).thenReturn("Kasun Perera");

        UpdateCandidateProfileRequest request = new UpdateCandidateProfileRequest();
        request.setFullName("Kasun Perera");
        request.setEmail("   ");

        assertThatThrownBy(() -> profileService.updateProfile(candidateId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Email cannot be blank");
    }

    @Test
    void updateProfileValidatesAndNormalizesPhone() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("Kasun Perera")).thenReturn("Kasun Perera");

        UpdateCandidateProfileRequest request = createValidRequest();
        request.setPhone("+94 (77) 123-4567");

        profileService.updateProfile(candidateId, request);
        assertThat(candidate.getContactNumber()).isEqualTo("+94 (77) 123-4567");

        // Invalid phone (< 7 digits)
        request.setPhone("123");
        assertThatThrownBy(() -> profileService.updateProfile(candidateId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Enter a valid phone number");
    }

    @Test
    void updateProfileRejectsDisabledCandidate() {
        candidate.setStatus(UserStatus.DISABLED);
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        UpdateCandidateProfileRequest request = new UpdateCandidateProfileRequest();
        assertThatThrownBy(() -> profileService.updateProfile(candidateId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("account is inactive");
    }

    @Test
    void updateProfileHandlesCvUploadWhileJoiningTalentPool() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("Kasun Perera")).thenReturn("Kasun Perera");

        MockMultipartFile cvFile = new MockMultipartFile("cv", "cv.pdf", "application/pdf", new byte[]{1});
        UpdateCandidateProfileRequest request = createValidRequest();
        request.setAddToTalentPool(true);
        request.setCv(cvFile);

        profileService.updateProfile(candidateId, request);

        assertThat(candidate.isInTalentPool()).isTrue();
        verify(auditService).recordEvent(candidateId, "JOINED_POOL", true, null);
        verify(cvService).uploadCv(candidate, cvFile);
    }

    @Test
    void updateProfileHandlesCvRemovalWhileInTalentPool() {
        candidate.setInTalentPool(true);
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("Kasun Perera")).thenReturn("Kasun Perera");

        UpdateCandidateProfileRequest request = createValidRequest();
        request.setAddToTalentPool(true);
        request.setRemoveCv(true);

        profileService.updateProfile(candidateId, request);

        verify(cvService).removeActiveCv(candidate);
    }

    @Test
    void updateProfileRemovesPhotoWhenRemovePhotoIsTrueAndPhotoUrlIsNull() {
        candidate.setPhotoUrl(null);
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("Kasun Perera")).thenReturn("Kasun Perera");

        UpdateCandidateProfileRequest request = createValidRequest();
        request.setRemovePhoto(true);

        profileService.updateProfile(candidateId, request);

        verify(storageService, never()).delete(any());
        assertThat(candidate.getPhotoUrl()).isNull();
    }

    @Test
    void updateProfileReplacesExistingPhotoWhenPhotoUrlNotNull() {
        candidate.setPhotoUrl("/api/v1/public/media/candidate/" + candidateId + "/photos/old.png");
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("Kasun Perera")).thenReturn("Kasun Perera");

        MockMultipartFile newPhoto = new MockMultipartFile("photo", "new.jpg", "image/jpeg", new byte[]{1, 2, 3});
        UpdateCandidateProfileRequest request = createValidRequest();
        request.setPhoto(newPhoto);

        when(storageService.store(eq("candidate"), eq(candidateId), eq("photos"), eq("new.jpg"), any(), any()))
                .thenReturn("/api/v1/public/media/candidate/" + candidateId + "/photos/new.jpg");

        profileService.updateProfile(candidateId, request);

        verify(storageService).delete("/api/v1/public/media/candidate/" + candidateId + "/photos/old.png");
        assertThat(candidate.getPhotoUrl()).contains("new.jpg");
    }

    @Test
    void updateProfileThrowsIllegalStateExceptionWhenPhotoGetBytesFails() throws IOException {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("Kasun Perera")).thenReturn("Kasun Perera");

        MultipartFile mockPhoto = mock(MultipartFile.class);
        when(mockPhoto.isEmpty()).thenReturn(false);
        when(mockPhoto.getOriginalFilename()).thenReturn("avatar.png");
        when(mockPhoto.getBytes()).thenThrow(new IOException("File read error"));

        UpdateCandidateProfileRequest request = createValidRequest();
        request.setPhoto(mockPhoto);

        assertThatThrownBy(() -> profileService.updateProfile(candidateId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to read photo data");
    }

    @Test
    void updateProfileCandidateAlreadyInPoolDoesNotDuplicateJoinedPoolAudit() {
        candidate.setInTalentPool(true);
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("Kasun Perera")).thenReturn("Kasun Perera");

        UpdateCandidateProfileRequest request = createValidRequest();
        request.setAddToTalentPool(true);

        profileService.updateProfile(candidateId, request);

        verify(auditService, never()).recordEvent(eq(candidateId), eq("JOINED_POOL"), eq(true), any());
    }

    @Test
    void updateProfileCandidateNotInPoolAndAddToTalentPoolFalseDoesNothing() {
        candidate.setInTalentPool(false);
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("Kasun Perera")).thenReturn("Kasun Perera");

        UpdateCandidateProfileRequest request = createValidRequest();
        request.setAddToTalentPool(false);

        profileService.updateProfile(candidateId, request);

        verify(cvService, never()).deactivateForLeavingPool(any());
    }

    @Test
    void updateProfileWithNullPhoneNormalizesToNull() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("Kasun Perera")).thenReturn("Kasun Perera");

        UpdateCandidateProfileRequest request = createValidRequest();
        request.setPhone(null);

        profileService.updateProfile(candidateId, request);

        assertThat(candidate.getContactNumber()).isNull();
    }

    @Test
    void updateProfileRejectsCvUploadWhenAddToTalentPoolIsFalse() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("Kasun Perera")).thenReturn("Kasun Perera");

        MockMultipartFile cvFile = new MockMultipartFile("cv", "cv.pdf", "application/pdf", new byte[]{1, 2});
        UpdateCandidateProfileRequest request = createValidRequest();
        request.setAddToTalentPool(false);
        request.setCv(cvFile);

        assertThatThrownBy(() -> profileService.updateProfile(candidateId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Cannot upload a CV when not participating in the Talent Pool");
    }

    @Test
    void updateProfilePreservesOldPhotoIfStoreFails() {
        candidate.setPhotoUrl("/api/v1/public/media/candidate/" + candidateId + "/photos/old.png");
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(htmlSanitizer.sanitize("Kasun Perera")).thenReturn("Kasun Perera");

        MockMultipartFile newPhoto = new MockMultipartFile("photo", "new.jpg", "image/jpeg", new byte[]{1, 2, 3});
        UpdateCandidateProfileRequest request = createValidRequest();
        request.setPhoto(newPhoto);

        when(storageService.store(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Storage disk full"));

        assertThatThrownBy(() -> profileService.updateProfile(candidateId, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Storage disk full");

        // Old photo must NOT have been deleted
        verify(storageService, never()).delete("/api/v1/public/media/candidate/" + candidateId + "/photos/old.png");
        assertThat(candidate.getPhotoUrl()).isEqualTo("/api/v1/public/media/candidate/" + candidateId + "/photos/old.png");
    }
}
