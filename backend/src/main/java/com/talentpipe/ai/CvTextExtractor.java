package com.talentpipe.ai;

import com.talentpipe.ai.dto.ExtractedCvResult;

/**
 * Strategy interface for extracting clean text from uploaded PDF/DOCX CV files.
 */
public interface CvTextExtractor {

    ExtractedCvResult extract(byte[] fileBytes, String filename, String mimeType);
}
