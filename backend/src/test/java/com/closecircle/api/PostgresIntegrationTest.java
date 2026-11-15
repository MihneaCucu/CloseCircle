package com.closecircle.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for tests that run the whole application against a real PostgreSQL migrated by Flyway.
 * One container is shared by all subclasses (started on first use, removed when the JVM exits), so
 * Spring can reuse a single application context. Skipped when Docker is unavailable.
 */
@SpringBootTest(properties = "closecircle.jwt.secret=test-only-secret-that-is-at-least-32-bytes-long")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    // Point both the application and Flyway at the container, so no developer environment
    // variable can send the tests to a shared database.
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("FLYWAY_DATABASE_URL", POSTGRES::getJdbcUrl);
        registry.add("FLYWAY_DATABASE_USERNAME", POSTGRES::getUsername);
        registry.add("FLYWAY_DATABASE_PASSWORD", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper json;

    /** A user created through the real register endpoint. */
    protected record TestUser(UUID id, String email, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    /** Registers a new user with a unique email, so tests never collide in the shared database. */
    protected TestUser registerUser(String displayName) throws Exception {
        String email = displayName.toLowerCase() + "-" + UUID.randomUUID() + "@example.invalid";
        JsonNode body = body(postJson("/api/v1/auth/register",
                Map.of("email", email, "password", "correct-horse-example", "displayName", displayName))
                .andExpect(status().isCreated()));
        return new TestUser(UUID.fromString(body.at("/user/userId").asText()), email, body.at("/accessToken").asText());
    }

    protected ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
