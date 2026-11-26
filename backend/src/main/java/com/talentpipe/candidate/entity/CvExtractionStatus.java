package com.talentpipe.candidate.entity;

/** How far reading the text of a {@link CandidateCv} has got (V26). */
public enum CvExtractionStatus {

    /** Not read yet, or the AI service could not be reached. Tried again later. */
    PENDING,

    /** Read: the text is stored. */
    EXTRACTED,

    /** Read, but there is too little text in it to work with — a scan, for example. */
    EMPTY_OR_SCANNED,

    /** The file itself cannot be read. */
    FAILED
}
