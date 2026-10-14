package com.talentpipe.candidate.service;

import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.candidate.dto.CandidateCvResponse;
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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Facade service for candidate profile management, coordinating personal details,
 * profile photos, talent pool membership, and versioned CV uploads.
 */
@Service
public class CandidateProfileService {

    private static final Logger log = LoggerFactory.getLogger(CandidateProfileService.class);
    private static final Pattern NIC_COMPACT_PATTERN = Pattern.compile("^[A-Za-z0-9]{5,20}$");
    private static final Pattern NIC_RAW_PATTERN = Pattern.compile("^[A-Za-z0-9\\s-]+$");
    private static final String PHOTO_CATEGORY = "photos";

    private final CandidateRepository candidateRepository;
    private final CandidateCvService cvService;
    private final CandidatePhotoValidator photoValidator;
    private final FileStorageService storageService;
    private final TalentPoolAuditService auditService;
    private final HtmlSanitizer htmlSanitizer;

    public CandidateProfileService(CandidateRepository candidateRepository,
                                   CandidateCvService cvService,
                                   CandidatePhotoValidator photoValidator,
                                   FileStorageService storageService,
                                   TalentPoolAuditService auditService,
                                   HtmlSanitizer htmlSanitizer) {
        this.candidateRepository = candidateRepository;
        this.cvService = cvService;
        this.photoValidator = photoValidator;
        this.storageService = storageService;
        this.auditService = auditService;
        this.htmlSanitizer = htmlSanitizer;
    }

    /**
     * Retrieves the candidate's profile along with active CV information.
     */
    @Transactional(readOnly = true)
    public MyCandidateProfileResponse getProfile(UUID candidateId) {
        Candidate candidate = findVerifiedCandidate(candidateId);
        Optional<CandidateCv> activeCv = cvService.getActiveCv(candidateId);

        CandidateCvResponse cvResponse = activeCv
                .map(cv -> new CandidateCvResponse(cv.getFileName(), cv.getFileSizeBytes(), cv.getStorageUrl()))
                .orElse(null);

        return new MyCandidateProfileResponse(
                candidate.getId(),
                candidate.getFullName(),
                candidate.getEmail(),
                candidate.getContactNumber(),
                candidate.getIdentityCardNumber(),
                candidate.getPhotoUrl(),
                candidate.isInTalentPool(),
                cvResponse);
    }

    /**
     * Updates candidate personal data, profile photo, and talent pool CV.
     */
    @Transactional
    public MyCandidateProfileResponse updateProfile(UUID candidateId, UpdateCandidateProfileRequest request) {
        Candidate candidate = findVerifiedCandidate(candidateId);

        String sanitizedFullName = sanitizeFullName(request.getFullName());
        String normalizedEmail = normalizeEmail(request.getEmail(), candidate);
        String normalizedNic = normalizeAndValidateNic(request.getIdentityCardNumber());
        String normalizedPhone = normalizeAndValidatePhone(request.getPhone());

        handlePhotoUpdate(candidate, request.getPhoto(), request.getRemovePhoto());
        handleTalentPoolAndCv(candidate, request.isAddToTalentPool(), request.getCv(), request.getRemoveCv());

        candidate.updateProfile(
                sanitizedFullName,
                normalizedEmail,
                normalizedNic,
                normalizedPhone,
                request.isAddToTalentPool());

        candidateRepository.save(candidate);
        log.info("Successfully updated profile for candidate {}", candidateId);

        return getProfile(candidateId);
    }

    // ---------------------------------------------------------------- private

    private Candidate findVerifiedCandidate(UUID candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        if (candidate.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new AccountNotVerifiedException("Email is not verified yet. Please verify your email first.");
        }
        if (candidate.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidRequestException("Candidate account is inactive.");
        }
        return candidate;
    }

    private String sanitizeFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new InvalidRequestException("Full name cannot be blank.");
        }
        String sanitized = htmlSanitizer.sanitize(fullName).trim();
        if (sanitized.isEmpty()) {
            throw new InvalidRequestException("Full name must contain valid text characters.");
        }
        return sanitized;
    }

    private String normalizeEmail(String email, Candidate candidate) {
        if (email == null || email.isBlank()) {
            throw new InvalidRequestException("Email cannot be blank.");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals(candidate.getEmail()) && candidateRepository.existsByEmail(normalized)) {
            throw new DuplicateResourceException("Email '" + normalized + "' is already in use by another candidate.");
        }
        return normalized;
    }

    private String normalizeAndValidateNic(String nic) {
        if (nic == null || nic.isBlank()) {
            throw new InvalidRequestException("ID number cannot be blank.");
        }
        String trimmed = nic.trim();
        if (!NIC_RAW_PATTERN.matcher(trimmed).matches()) {
            throw new InvalidRequestException("ID number contains invalid characters.");
        }
        String compact = trimmed.replaceAll("[\\s-]", "");
        if (!NIC_COMPACT_PATTERN.matcher(compact).matches()) {
            throw new InvalidRequestException("Enter a valid ID number, like 200012345678 or 912345678V.");
        }
        return compact.toUpperCase(Locale.ROOT);
    }

    private String normalizeAndValidatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String trimmed = phone.trim();
        String digitsOnly = trimmed.replaceAll("[^0-9]", "");
        if (digitsOnly.length() < 7 || digitsOnly.length() > 15) {
            throw new InvalidRequestException("Enter a valid phone number, like +94 77 123 4567.");
        }
        return trimmed;
    }

    private void handlePhotoUpdate(Candidate candidate, MultipartFile photo, Boolean removePhoto) {
        if (Boolean.TRUE.equals(removePhoto)) {
            if (candidate.getPhotoUrl() != null) {
                storageService.delete(candidate.getPhotoUrl());
                candidate.setPhotoUrl(null);
                log.info("Deleted profile photo for candidate {}", candidate.getId());
            }
            return;
        }

        if (photo != null && !photo.isEmpty()) {
            photoValidator.validate(photo);
            String oldPhotoUrl = candidate.getPhotoUrl();

            try {
                String photoUrl = storageService.store(
                        "candidate",
                        candidate.getId(),
                        PHOTO_CATEGORY,
                        photo.getOriginalFilename(),
                        photo.getBytes(),
                        photo.getContentType());
                candidate.setPhotoUrl(photoUrl);
                log.info("Stored new profile photo for candidate {}", candidate.getId());

                if (oldPhotoUrl != null) {
                    storageService.delete(oldPhotoUrl);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read photo data", e);
            }
        }
    }

    private void handleTalentPoolAndCv(Candidate candidate, boolean addToTalentPool,
                                       MultipartFile cvFile, Boolean removeCv) {
        boolean wasInPool = candidate.isInTalentPool();

        if (addToTalentPool) {
            if (!wasInPool) {
                auditService.recordEvent(candidate.getId(), "JOINED_POOL", true, null);
                log.info("Candidate {} joined the talent pool", candidate.getId());
            }
            if (cvFile != null && !cvFile.isEmpty()) {
                cvService.uploadCv(candidate, cvFile);
            } else if (Boolean.TRUE.equals(removeCv)) {
                cvService.removeActiveCv(candidate);
            }
        } else {
            if (cvFile != null && !cvFile.isEmpty()) {
                throw new InvalidRequestException(
                        "Cannot upload a CV when not participating in the Talent Pool. Set addToTalentPool to true.");
            }
            if (wasInPool) {
                cvService.deactivateForLeavingPool(candidate);
            }
        }
    }
}
