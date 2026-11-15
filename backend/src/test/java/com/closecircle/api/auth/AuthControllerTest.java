package com.closecircle.api.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.closecircle.api.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Request validation and error format for the auth endpoints. */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "closecircle.jwt.secret=test-only-secret-that-is-at-least-32-bytes-long")
class AuthControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthService authService;

    @Test
    void missingFieldsAreReportedPerField() throws Exception {
        register("{}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("displayName")))
                .andExpect(jsonPath("$.errors[*].code", hasItem("REQUIRED")));
        verifyNoInteractions(authService);
    }

    @Test
    void invalidEmailIsRejected() throws Exception {
        register("""
                {"email":"not-an-email","password":"correct-horse-example","displayName":"Alex"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void shortPasswordIsRejectedWithoutEchoingIt() throws Exception {
        register("""
                {"email":"alex@example.invalid","password":"short-pw","displayName":"Alex"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_LENGTH"))
                .andExpect(content().string(not(containsString("short-pw"))));
    }

    @Test
    void passwordLongerThan72BytesIsRejected() throws Exception {
        // 37 x "ă" is only 37 characters but 74 bytes in UTF-8.
        String password = "ă".repeat(37);
        register("{\"email\":\"alex@example.invalid\",\"password\":\"" + password + "\",\"displayName\":\"Alex\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void blankDisplayNameIsRejectedAfterTrimming() throws Exception {
        register("""
                {"email":"alex@example.invalid","password":"correct-horse-example","displayName":"   "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("displayName"));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        register("{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void duplicateEmailIsAConflict() throws Exception {
        given(authService.register(any())).willThrow(new AuthService.EmailAlreadyRegisteredException());
        register("""
                {"email":"alex@example.invalid","password":"correct-horse-example","displayName":"Alex"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void wrongCredentialsAreUnauthorized() throws Exception {
        given(authService.login(any())).willThrow(new AuthService.InvalidCredentialsException());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"alex@example.invalid","password":"wrong-password-123"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    private ResultActions register(String json) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
