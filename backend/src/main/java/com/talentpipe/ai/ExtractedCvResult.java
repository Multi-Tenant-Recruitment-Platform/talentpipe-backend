package com.talentpipe.ai;

/**
 * What {@link CvTextExtractor} found in a CV.
 *
 * @param text           the CV as plain text; empty when there is none
 * @param charCount      how many readable characters were found
 * @param pageCount      how many pages the file has
 * @param emptyOrScanned whether there is too little text to work with — a scan, or
 *                       a design exported as outlines
 */
public record ExtractedCvResult(String text, int charCount, int pageCount, boolean emptyOrScanned) {

    /** Leaves the text out: it is a person's CV, and this ends up in logs. */
    @Override
    public String toString() {
        return "ExtractedCvResult[charCount=" + charCount + ", pageCount=" + pageCount
                + ", emptyOrScanned=" + emptyOrScanned + "]";
    }
}
