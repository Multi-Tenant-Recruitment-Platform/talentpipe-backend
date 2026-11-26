package com.talentpipe.ai;

/**
 * Turns an uploaded CV into plain text.
 *
 * <p>A port: whoever needs the text depends on this interface, and the adapter
 * that talks to the AI service (MRP-359) implements it. There may be no
 * implementation at all — the platform runs without the AI service, and CVs
 * then simply wait to be extracted — so inject it as an
 * {@code ObjectProvider<CvTextExtractor>} rather than as a required bean.</p>
 *
 * <p>Calls are slow and can fail: never call this inside a database
 * transaction.</p>
 */
public interface CvTextExtractor {

    /**
     * @param fileBytes the file as uploaded
     * @param filename  its original name
     * @param mimeType  its declared type
     * @return the text, and whether there was any
     * @throws UnreadableCvException when the file itself cannot be read, so trying
     *                               again would not help
     * @throws RuntimeException      anything else — the service is down, slow or
     *                               busy — meaning "try again later"
     */
    ExtractedCvResult extract(byte[] fileBytes, String filename, String mimeType);
}
