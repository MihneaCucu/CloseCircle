package com.closecircle.api.config;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Writes Spring Security's 401 responses. By default they have an empty body; the contract
 * (docs/api-contract.md, section 6) requires Problem Details with a {@code code} on every error.
 * The status and {@code WWW-Authenticate: Bearer} header still come from Spring's standard entry point.
 */
class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final AuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
    private final ObjectMapper json;

    ProblemAuthenticationEntryPoint(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        bearer.commence(request, response, exception);

        // A token was sent but rejected (bad signature, expired, wrong issuer, missing claims...)
        // versus no token at all. The body never says which check failed.
        boolean tokenRejected = exception instanceof OAuth2AuthenticationException;
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", tokenRejected ? "Invalid token" : "Authentication required");
        problem.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        problem.put("detail", tokenRejected
                ? "The access token is invalid or has expired. Log in again."
                : "This endpoint requires a Bearer access token.");
        problem.put("instance", request.getRequestURI());
        problem.put("code", tokenRejected ? "INVALID_TOKEN" : "AUTHENTICATION_REQUIRED");

        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }
}
