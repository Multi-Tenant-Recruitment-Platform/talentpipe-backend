package com.talentpipe.candidate.entity;

import com.talentpipe.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;

/**
 * A tracked, versioned curriculum vitae (CV) uploaded by a candidate.
 *
 * <p>Only one CV is marked active per candidate at any given time. When a new
 * CV is uploaded, the existing active CV is archived rather than deleted,
 * preserving audit history for recruiters and analytics.</p>
 *
 * <p>The text of a CV is extracted in the background (ADR-15), so two
 * transactions may change one row at the same time: one archiving it, one
 * storing its text. {@code @DynamicUpdate} writes only the columns each of them
 * changed; without it, whichever finished last would undo the other.</p>
 */
@Entity
@Table(name = "candidate_cvs")
@DynamicUpdate
@SuppressWarnings("java:S2160") // JPA entities inherit identity-based equals/hashCode from BaseEntity
public class CandidateCv extends BaseEntity {

    public static final String DEFAULT_STORAGE_URL = "/api/v1/candidates/me/cv/download";

    /**
     * The longest text kept — some sixty full pages. No real CV comes near it; it
     * bounds a hostile one, since a DOCX can unpack to many times its size.
     */
    static final int MAX_EXTRACTED_CHARS = 200_000;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "storage_url", columnDefinition = "TEXT")
    private String storageUrl;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "file_hash_sha256", nullable = false, length = 64)
    private String fileHashSha256;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 20)
    private CvExtractionStatus extractionStatus = CvExtractionStatus.PENDING;

    @Column(name = "text_extracted_at")
    private Instant textExtractedAt;

    protected CandidateCv() {
        // for JPA
    }

    public CandidateCv(UUID candidateId, String fileName, String storageKey,
                       String mimeType, long fileSizeBytes,
                       String fileHashSha256, int version) {
        this.candidateId = candidateId;
        this.fileName = fileName;
        this.storageKey = storageKey;
        this.storageUrl = DEFAULT_STORAGE_URL;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
        this.fileHashSha256 = fileHashSha256;
        this.version = version;
        this.isActive = true;
        this.uploadedAt = Instant.now();
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getStorageUrl() {
        return storageUrl;
    }

    public void setStorageUrl(String storageUrl) {
        this.storageUrl = storageUrl;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getFileHashSha256() {
        return fileHashSha256;
    }

    public int getVersion() {
        return version;
    }

    public boolean isActive() {
        return isActive;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    /**
     * The CV as plain text, or {@code null} until it has been extracted. Personal
     * data: for screening only — never put it in a response or a log.
     */
    public String getExtractedText() {
        return extractedText;
    }

    public CvExtractionStatus getExtractionStatus() {
        return extractionStatus;
    }

    public Instant getTextExtractedAt() {
        return textExtractedAt;
    }

    /**
     * Marks this CV as archived and inactive.
     */
    public void archive(Instant now) {
        this.isActive = false;
        this.archivedAt = now;
    }

    /** Stores what was found in the file. */
    public void recordExtraction(String text, boolean emptyOrScanned, Instant extractedAt) {
        this.extractedText = storable(text);
        this.extractionStatus = emptyOrScanned || extractedText.isBlank()
                ? CvExtractionStatus.EMPTY_OR_SCANNED
                : CvExtractionStatus.EXTRACTED;
        this.textExtractedAt = extractedAt;
    }

    /** Marks the file as one that cannot be read. */
    public void recordExtractionFailure() {
        this.extractedText = null;
        this.extractionStatus = CvExtractionStatus.FAILED;
        this.textExtractedAt = null;
    }

    /** Takes over the result of a CV whose file is identical. */
    public void copyExtractionFrom(CandidateCv identical) {
        if (!fileHashSha256.equals(identical.fileHashSha256)) {
            throw new IllegalArgumentException("Not the same file");
        }
        this.extractedText = identical.extractedText;
        this.extractionStatus = identical.extractionStatus;
        this.textExtractedAt = identical.textExtractedAt;
    }

    /** PostgreSQL cannot store NUL characters, which the text of a PDF can contain. */
    private static String storable(String text) {
        String clean = text == null ? "" : text.replace("\0", "");
        if (clean.length() <= MAX_EXTRACTED_CHARS) {
            return clean;
        }
        // Never between the two halves of a character outside the basic plane.
        int end = Character.isHighSurrogate(clean.charAt(MAX_EXTRACTED_CHARS - 1))
                ? MAX_EXTRACTED_CHARS - 1
                : MAX_EXTRACTED_CHARS;
        return clean.substring(0, end);
    }
}
