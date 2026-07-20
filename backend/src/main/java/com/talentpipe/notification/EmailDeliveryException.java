package com.talentpipe.notification;

/**
 * Raised by an {@link EmailService} when a message could not be delivered.
 *
 * <p>Never propagates to an HTTP caller: delivery happens on the async
 * notification path, after the originating transaction has committed, so a
 * mail outage marks the attempt FAILED instead of failing registration,
 * password reset or invitation.</p>
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
