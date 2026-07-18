package com.talentpipe.common.exception;

/**
 * Maps to 409. Thrown when creating a resource that collides with an existing
 * one (e.g. a tenant subdomain or a per-tenant user email already in use).
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
