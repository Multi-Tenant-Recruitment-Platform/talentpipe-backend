package com.talentpipe.common.exception;

/**
 * Maps to 404. Thrown when a resource does not exist — or exists but belongs
 * to another tenant. Both cases MUST look identical to the caller so resource
 * existence never leaks across tenant boundaries (deliberately 404, not 403).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
