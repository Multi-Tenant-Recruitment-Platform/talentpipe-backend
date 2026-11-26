package com.talentpipe.ai;

/**
 * The CV file itself cannot be read — corrupted, encrypted or not what it claims
 * to be — so extracting it again would fail the same way. Anything temporary must
 * be thrown as something else (see {@link CvTextExtractor#extract}).
 */
public class UnreadableCvException extends RuntimeException {

    public UnreadableCvException(String message) {
        super(message);
    }

    public UnreadableCvException(String message, Throwable cause) {
        super(message, cause);
    }
}
