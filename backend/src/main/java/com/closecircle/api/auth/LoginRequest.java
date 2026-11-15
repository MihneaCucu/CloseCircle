package com.closecircle.api.auth;

import com.closecircle.api.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;

/** Only presence is checked, so a wrong login is never explained as a format problem. */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank @MaxUtf8Bytes(AuthLimits.PASSWORD_MAX_BYTES) String password) {

    public LoginRequest {
        email = email == null ? null : email.strip();
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
