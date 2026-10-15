package com.talentpipe.candidate.entity;

import com.talentpipe.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A tracked, versioned curriculum vitae (CV) uploaded by a candidate.
 *
 * <p>Only one CV is marked active per candidate at any given time. When a new
 * CV is uploaded, the existing active CV is archived rather than deleted,
 * preserving audit history for recruiters and analytics.</p>
 */
@Entity
@Table(name = "candidate_cvs")
public class CandidateCv extends BaseEntity {

    public static final String DEFAULT_STORAGE_URL = "/api/v1/candidates/me/cv/download";

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

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
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
     * Marks this CV as archived and inactive.
     */
    public void archive(Instant now) {
        this.isActive = false;
        this.archivedAt = now;
    }
}
