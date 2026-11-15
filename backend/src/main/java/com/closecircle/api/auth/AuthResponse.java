package com.closecircle.api.auth;

import java.time.Instant;

import com.closecircle.api.user.User;
import com.closecircle.api.user.UserResponse;

/** Returned by both register and login, as the contract specifies. */
public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {
    static AuthResponse bearer(TokenService.IssuedToken token, User user) {
        return new AuthResponse(token.value(), "Bearer", token.expiresAt(), UserResponse.from(user));
    }

    @Override
    public String toString() {
        return "AuthResponse[accessToken=***, tokenType=" + tokenType + ", expiresAt=" + expiresAt
                + ", user=" + user + "]";
    }
}
