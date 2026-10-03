package com.talentpipe.common.exception;

/**
 * Maps to 409. Thrown when an update was made against a version of a resource
 * that is no longer the stored one — someone else saved first.
 *
 * <p>Distinct from {@link DuplicateResourceException}, which is also a 409 but
 * means "this would collide with another resource". Here nothing collides: the
 * request is simply based on stale data, and the remedy is to reload and
 * retry, not to pick a different value.</p>
 */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(String message) {
        super(message);
    }
}
