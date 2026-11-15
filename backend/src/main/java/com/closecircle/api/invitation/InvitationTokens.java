package com.closecircle.api.invitation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * One-time invitation tokens. The database keeps only a SHA-256 hash ({@code token_hash}, required
 * and unique). How a raw token reaches the invitee (email, link) is still an open team decision, so
 * for now it is never returned; the in-app flow identifies invitations by ID instead.
 */
final class InvitationTokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    private InvitationTokens() {
    }

    /** 256 random bits, URL-safe. */
    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * A plain SHA-256 is enough here, unlike for passwords: the token is 256 random bits, so it can't be
     * guessed or brute-forced. A deterministic hash also lets a future accept-by-token flow look it up.
     */
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }
}
