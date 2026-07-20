package com.talentpipe.notification;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Resend transport (<a href="https://resend.com/docs">resend.com/docs</a>):
 * {@code POST https://api.resend.com/emails} with a
 * {@code Authorization: Bearer <API key>} header and a JSON body of
 * {@code from}, {@code to}, {@code subject}, {@code html}. A 200 response
 * carries the message id; anything else is a delivery failure.
 *
 * <p>Instantiated by {@link EmailConfig} only when an API key is configured —
 * it is not a component, so no key can ever be baked in as a default. The
 * {@link RestTemplate} it receives has finite connect/read timeouts: an
 * unresponsive mail API must not pin a worker thread indefinitely.</p>
 *
 * <p><strong>Deliverability:</strong> the configured sender must belong to a
 * verified sending domain to reach arbitrary recipients. With the sandbox
 * {@code onboarding@resend.dev} sender, Resend accepts the request but only
 * delivers to the account owner's own address — a 200 here is not proof the
 * recipient received anything. See README.</p>
 */
public class ResendEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);
    private static final String SEND_ENDPOINT = "https://api.resend.com/emails";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String fromAddress;

    public ResendEmailService(RestTemplate restTemplate, String apiKey, String fromAddress) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(EmailMessage message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "from", fromAddress,
                "to", message.to(),
                "subject", message.subject(),
                "html", message.html());

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    SEND_ENDPOINT,
                    org.springframework.http.HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new org.springframework.core.ParameterizedTypeReference<>() { });

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new EmailDeliveryException(
                        "Resend rejected the message with status " + response.getStatusCode());
            }

            Object messageId = response.getBody() == null ? null : response.getBody().get("id");
            // Recipient and subject only — the body carries a single-use token
            // and must never reach the logs.
            log.info("Email delivered via Resend (messageId={}, to={}, subject='{}')",
                    messageId, message.to(), message.subject());

        } catch (RestClientException ex) {
            // Covers 4xx/5xx from Resend as well as connect/read timeouts.
            throw new EmailDeliveryException("Resend delivery failed: " + ex.getMessage(), ex);
        }
    }
}
