package com.talentpipe.common.exception;

/**
 * Maps to 422. Thrown when a syntactically valid request violates a business
 * rule (as opposed to 400, which is reserved for malformed/invalid input).
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
