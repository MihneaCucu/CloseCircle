package com.closecircle.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs register, login and an authenticated request through the whole application,
 * against a throwaway PostgreSQL migrated by Flyway. Skipped when Docker is unavailable.
 */
@SpringBootTest(properties = "closecircle.jwt.secret=test-only-secret-that-is-at-least-32-bytes-long")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    // Point both the application and Flyway at the container, so no developer environment
    // variable can send the tests to a shared database.
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("FLYWAY_DATABASE_URL", POSTGRES::getJdbcUrl);
        registry.add("FLYWAY_DATABASE_USERNAME", POSTGRES::getUsername);
        registry.add("FLYWAY_DATABASE_PASSWORD", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

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

    private ResultActions register(String email, String password, String displayName) throws Exception {
        // A plain map, so values are sent exactly as given (untrimmed) and the server does the trimming.
        String request = json.writeValueAsString(
                Map.of("email", email, "password", password, "displayName", displayName));
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(request));
    }

    private ResultActions login(String email, String password) throws Exception {
        String request = json.writeValueAsString(Map.of("email", email, "password", password));
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(request));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
