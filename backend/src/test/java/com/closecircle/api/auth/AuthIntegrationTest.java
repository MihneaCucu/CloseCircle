package com.closecircle.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.closecircle.api.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Register, login and an authenticated request through the whole application and a real database. */
class AuthIntegrationTest extends PostgresIntegrationTest {

    @Test
    void registeredUserCanLogInAndCallAProtectedEndpoint() throws Exception {
        JsonNode registered = body(register("  Alex@Example.invalid ", "correct-horse-example", " Alex ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("Alex@Example.invalid"))
                .andExpect(jsonPath("$.user.displayName").value("Alex"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist()));
        String userId = registered.at("/user/userId").asText();

        // Stored as a BCrypt hash, never as the raw password.
        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE user_id = ?::uuid", String.class, userId);
        assertThat(storedHash).startsWith("$2").doesNotContain("correct-horse-example");

        // Login is case-insensitive on the email.
        JsonNode loggedIn = body(login("alex@example.INVALID", "correct-horse-example")
                .andExpect(status().isOk()));
        String token = loggedIn.at("/accessToken").asText();

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(content().string(not(containsString("correct-horse-example"))));
    }

    @Test
    void sameEmailWithDifferentCaseCannotRegisterTwice() throws Exception {
        register("taken@example.invalid", "correct-horse-example", "First").andExpect(status().isCreated());

        register("TAKEN@example.invalid", "another-password-123", "Second")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetIdenticalResponses() throws Exception {
        register("sam@example.invalid", "correct-horse-example", "Sam").andExpect(status().isCreated());

        String wrongPassword = login("sam@example.invalid", "wrong-password-123")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@example.invalid", "wrong-password-123")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Identical bodies, so the caller cannot tell whether the account exists.
        assertThat(json.readTree(unknownEmail)).isEqualTo(json.readTree(wrongPassword));
    }

    @Test
    void protectedEndpointRejectsMissingAndGarbageTokens() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    // Plain maps, so values are sent exactly as given (untrimmed) and the server does the trimming.
    private ResultActions register(String email, String password, String displayName) throws Exception {
        return postJson("/api/v1/auth/register",
                Map.of("email", email, "password", password, "displayName", displayName));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", Map.of("email", email, "password", password));
    }
}
