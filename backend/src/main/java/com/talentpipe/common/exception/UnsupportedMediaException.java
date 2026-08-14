package com.talentpipe.common.exception;

/**
 * Thrown when an uploaded file has an unsupported MIME type.
 * Mapped to HTTP 415 Unsupported Media Type by {@link GlobalExceptionHandler}.
 */
public class UnsupportedMediaException extends RuntimeException {

    public UnsupportedMediaException(String message) {
        super(message);
    }
}
