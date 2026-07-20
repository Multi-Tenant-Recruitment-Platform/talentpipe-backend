package com.talentpipe.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development transport: prints the email to the application log instead of
 * sending it. Active only when no {@code RESEND_API_KEY} is configured, so a
 * developer can complete verification, reset and invitation flows locally by
 * copying the link out of the backend log.
 *
 * <p>This is the ONLY place where a link (and therefore a raw single-use
 * token) may be logged, and it exists precisely because nothing is being
 * delivered. The moment a key is configured, {@link ResendEmailService} takes
 * over and the link never touches the logs.</p>
 */
public class ConsoleEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailService.class);

    @Override
    public void send(EmailMessage message) {
        log.info("""

                [EMAIL-CONSOLE] no RESEND_API_KEY configured - nothing was sent
                  To      : {}
                  Subject : {}
                  Body    :
                {}
                """,
                message.to(), message.subject(), message.html());
    }
}
