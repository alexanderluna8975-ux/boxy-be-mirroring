package com.boxy.boxy.core.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Shared helpers for the "generate an opaque random value, store only its hash" pattern used by
 * both the refresh-token cookie ({@code RefreshTokenService}) and the device-id cookie
 * ({@code DeviceService}) — a database read (backup, dump, careless log) must never be turned
 * back into a usable cookie value.
 */
public final class Hashing {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private Hashing() {
    }

    /** A URL-safe, unpadded base64 string from 32 random bytes — the same shape
     *  {@code RefreshTokenService} has always used for its raw token. */
    public static String randomOpaqueToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hex-encoded SHA-256 of {@code raw} — 64 characters, matching every
     *  {@code CHAR(64)}/{@code VARCHAR(64)} hash column in the schema. */
    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
