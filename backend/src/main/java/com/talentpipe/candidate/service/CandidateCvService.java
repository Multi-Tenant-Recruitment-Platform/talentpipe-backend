package com.talentpipe.candidate.service;

import com.talentpipe.candidate.entity.Candidate;
import com.talentpipe.candidate.entity.CandidateCv;
import com.talentpipe.candidate.repository.CandidateCvRepository;
import com.talentpipe.candidate.validator.CandidateCvValidator;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.common.storage.FileStorageService;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Manages candidate CV lifecycle, SHA-256 deduplication, versioning, and storage.
 */
@Service
public class CandidateCvService {

    private static final Logger log = LoggerFactory.getLogger(CandidateCvService.class);
    private static final String CV_CATEGORY = "cvs";
    private static final String DOWNLOAD_URL = "/api/v1/candidates/me/cv/download";

    private final CandidateCvRepository cvRepository;
    private final CandidateCvValidator cvValidator;
    private final FileStorageService storageService;
    private final TalentPoolAuditService auditService;

    public record CvDownloadPayload(String fileName, String mimeType, byte[] data) {}

    public CandidateCvService(CandidateCvRepository cvRepository,
                              CandidateCvValidator cvValidator,
                              FileStorageService storageService,
                              TalentPoolAuditService auditService) {
        this.cvRepository = cvRepository;
        this.cvValidator = cvValidator;
        this.storageService = storageService;
        this.auditService = auditService;
    }

    /** Returns the active CV for the candidate, if one exists. */
    @Transactional(readOnly = true)
    public Optional<CandidateCv> getActiveCv(UUID candidateId) {
        return cvRepository.findFirstByCandidateIdAndIsActiveTrue(candidateId);
    }

    /**
     * Validates, digests, stores, and versions a new CV for the candidate.
     * If the uploaded file is identical in content to the active CV, the upload is skipped idempotently.
     */
    @Transactional
    public CandidateCv uploadCv(Candidate candidate, MultipartFile file) {
        cvValidator.validate(file);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read CV file bytes", e);
        }

        String sha256 = calculateSha256(bytes);
        Optional<CandidateCv> currentActive = getActiveCv(candidate.getId());

        // Idempotency check: identical file content avoids redundant storage
        if (currentActive.isPresent() && sha256.equals(currentActive.get().getFileHashSha256())) {
            log.info("CV upload identical to active CV for candidate {}, skipping re-upload", candidate.getId());
            return currentActive.get();
        }

        Instant now = Instant.now();
        int nextVersion = 1;

        if (currentActive.isPresent()) {
            CandidateCv oldCv = currentActive.get();
            oldCv.archive(now);
            cvRepository.save(oldCv);
            nextVersion = oldCv.getVersion() + 1;
        }

        String storedKey = storageService.store(
                "candidate",
                candidate.getId(),
                CV_CATEGORY,
                file.getOriginalFilename(),
                bytes,
                file.getContentType());

        try {
            CandidateCv newCv = new CandidateCv(
                    candidate.getId(),
                    file.getOriginalFilename(),
                    storedKey,
                    DOWNLOAD_URL,
                    file.getContentType(),
                    file.getSize(),
                    sha256,
                    nextVersion);

            CandidateCv saved = cvRepository.save(newCv);
            UUID savedId = saved != null ? saved.getId() : null;
            auditService.recordEvent(candidate.getId(), "UPDATED_CV", true, savedId);
            log.info("Uploaded version {} CV for candidate {}", nextVersion, candidate.getId());
            return saved;
        } catch (RuntimeException e) {
            log.warn("Failed to persist CV record for candidate {}, cleaning up stored file {}",
                    candidate.getId(), storedKey, e);
            storageService.delete(storedKey);
            throw e;
        }
    }

    /**
     * Marks the active CV as removed while the candidate remains in the Talent Pool.
     */
    @Transactional
    public void removeActiveCv(Candidate candidate) {
        Optional<CandidateCv> current = getActiveCv(candidate.getId());
        if (current.isPresent()) {
            CandidateCv cv = current.get();
            cv.archive(Instant.now());
            cvRepository.save(cv);
            auditService.recordEvent(candidate.getId(), "REMOVED_CV", true, null);
            log.info("Removed active CV for candidate {}", candidate.getId());
        }
    }

    /**
     * Archives any active CV when a candidate leaves the Talent Pool.
     */
    @Transactional
    public void deactivateForLeavingPool(Candidate candidate) {
        Optional<CandidateCv> current = getActiveCv(candidate.getId());
        if (current.isPresent()) {
            CandidateCv cv = current.get();
            cv.archive(Instant.now());
            cvRepository.save(cv);
            auditService.recordEvent(candidate.getId(), "LEFT_POOL", false, null);
            log.info("Deactivated CV because candidate {} left the talent pool", candidate.getId());
        } else {
            auditService.recordEvent(candidate.getId(), "LEFT_POOL", false, null);
        }
    }

    /**
     * Loads the active CV content for download.
     */
    @Transactional(readOnly = true)
    public CvDownloadPayload loadActiveCvResource(UUID candidateId) {
        CandidateCv cv = getActiveCv(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("No active CV found for candidate"));

        byte[] data = storageService.load(cv.getStorageKey());
        return new CvDownloadPayload(cv.getFileName(), cv.getMimeType(), data);
    }

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }
}
