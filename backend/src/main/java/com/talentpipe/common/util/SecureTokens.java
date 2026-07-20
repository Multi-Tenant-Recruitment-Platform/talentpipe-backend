package com.talentpipe.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Shared helpers for single-use link tokens (email verification, password
 * reset, invitations) and refresh-token hashing.
 *
 * <p>Security model, identical across all token kinds: a 32-byte (256-bit)
 * cryptographically random value is URL-safe base64-encoded and travels only
 * in the emailed link; the database stores exclusively its SHA-256 hex hash.
 * SHA-256 (not bcrypt) is deliberate — the input is high-entropy random data,
 * not a guessable password, and lookups must be by exact hash.</p>
 */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureTokens() {
        // static utility
    }

    /** Generates a URL-safe base64-encoded 32-byte random token. */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex digest of a raw token — the only form ever persisted. */
    public static String sha256(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JCA spec — unreachable on a compliant JVM.
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
