package com.talentpipe.application.screening;

/** No score could be produced; the message is shown to recruiters and never quotes the CV. */
public class ScreeningUnavailableException extends RuntimeException {

    public ScreeningUnavailableException(String message) {
        super(message);
    }

    public ScreeningUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
