package com.closecircle.api.auth;

/** Password policy, documented in docs/api-contract.md, section 4. */
final class AuthLimits {
    static final int PASSWORD_MIN_CHARS = 12;

    /** BCrypt ignores everything after 72 bytes, so longer passwords are rejected instead of silently cut. */
    static final int PASSWORD_MAX_BYTES = 72;

    private AuthLimits() {
    }
}
