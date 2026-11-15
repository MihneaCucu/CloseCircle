package com.closecircle.api.auth;

import com.closecircle.api.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank
        @Size(min = AuthLimits.PASSWORD_MIN_CHARS, message = "must be at least {min} characters")
        @MaxUtf8Bytes(AuthLimits.PASSWORD_MAX_BYTES)
        String password,
        @NotBlank @Size(max = 100) String displayName) {

    /** Trims surrounding whitespace before validation runs. The password is left exactly as typed. */
    public RegisterRequest {
        email = email == null ? null : email.strip();
        displayName = displayName == null ? null : displayName.strip();
    }

    /** Records print every field by default; keep the password out of logs. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=***, displayName=" + displayName + "]";
    }
}
