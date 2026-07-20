package com.talentpipe.notification;

/**
 * Email transport contract. One implementation is active per environment,
 * selected in {@link EmailConfig} by whether {@code RESEND_API_KEY} is set:
 * {@link ResendEmailService} when it is, {@link ConsoleEmailService} otherwise.
 *
 * <p>Implementations are <strong>synchronous and throwing</strong> — they
 * report success or failure honestly. Asynchrony and failure recording are the
 * responsibility of {@link NotificationDispatcher}, which is the only caller.
 * Business services never invoke a transport directly; they publish a
 * {@link com.talentpipe.notification.event.NotificationRequestedEvent}.</p>
 */
public interface EmailService {

    /**
     * Delivers a message, blocking until the transport confirms it.
     *
     * @throws EmailDeliveryException if the message was not accepted
     */
    void send(EmailMessage message);
}
