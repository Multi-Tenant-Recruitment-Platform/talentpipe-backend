package com.talentpipe.candidate.dto;

/**
 * Metadata for a candidate's stored CV / resume.
 *
 * @param fileName  original name of the uploaded file
 * @param sizeBytes size of the file in bytes
 * @param url       access/download URL for the CV
 */
public record CandidateCvResponse(
        String fileName,
        Long sizeBytes,
        String url
) {
}
