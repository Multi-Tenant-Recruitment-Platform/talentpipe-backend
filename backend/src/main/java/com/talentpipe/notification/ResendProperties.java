package com.talentpipe.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resend transport configuration.
 *
 * @param apiKey Resend API key, from {@code RESEND_API_KEY}. Deliberately has
 *               no in-repo default — when blank the application runs in console
 *               mode rather than falling back to some shared key.
 * @param from   sender address, from {@code MAIL_FROM}. Resend only delivers to
 *               arbitrary recipients when this address belongs to a VERIFIED
 *               sending domain; the {@code onboarding@resend.dev} sandbox
 *               sender reaches only the Resend account owner's own inbox.
 */
@ConfigurationProperties(prefix = "talentpipe.resend")
public record ResendProperties(String apiKey, String from) {

    /** True when a real API key is configured (i.e. emails actually go out). */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
