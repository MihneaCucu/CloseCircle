package com.closecircle.api.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import javax.crypto.spec.SecretKeySpec;

import com.closecircle.api.auth.AuthController;
import com.closecircle.api.auth.AuthResponse;
import com.closecircle.api.auth.AuthService;
import com.closecircle.api.user.UserController;
import com.closecircle.api.user.UserResponse;
import com.closecircle.api.user.UserService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Checks which requests get past Spring Security. Services are mocked; no database is needed. */
@WebMvcTest({UserController.class, AuthController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = "closecircle.jwt.secret=" + SecurityConfigTest.SECRET)
class SecurityConfigTest {
    static final String SECRET = "test-only-secret-that-is-at-least-32-bytes-long";
    static final String USER_ID = "3f91d50d-98ab-4f20-b450-5f3311b28b81";
    static final String ME = "/api/v1/users/me";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserService userService;

    @MockitoBean
    AuthService authService;

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        mvc.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void rejectedTokenGetsAProblemDetailsBody() throws Exception {
        // Teammates' checks rely on this body: a token with a non-UUID subject is now rejected
        // by Spring Security before any controller runs.
        mvc.perform(get(ME).header("Authorization", bearer(SECRET, claims -> claims.subject("not-a-uuid"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Invalid token"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(ME))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void validTokenPassesAuthentication() throws Exception {
        given(userService.getProfile(UUID.fromString(USER_ID)))
                .willReturn(new UserResponse(UUID.fromString(USER_ID), "alex@example.invalid", "Alex", Instant.now()));

        mvc.perform(get(ME).header("Authorization", bearer(SECRET, claims -> { })))
                .andExpect(status().isOk());
    }

    @Test
    void tokenWithWrongIssuerIsRejected() throws Exception {
        mvc.perform(get(ME).header("Authorization", bearer(SECRET, claims -> claims.issuer("someone-else"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        String forged = bearer("a-different-secret-that-is-also-32-bytes-long", claims -> { });
        mvc.perform(get(ME).header("Authorization", forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        mvc.perform(get(ME).header("Authorization",
                        bearer(SECRET, claims -> claims.issuedAt(past.minusSeconds(60)).expiresAt(past))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutExpiryIsRejected() throws Exception {
        mvc.perform(get(ME).header("Authorization", bearer(SECRET, claims -> claims.claims(c -> c.remove("exp")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutUuidSubjectIsRejected() throws Exception {
        mvc.perform(get(ME).header("Authorization", bearer(SECRET, claims -> claims.subject("alex"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(ME).header("Authorization", bearer(SECRET, claims -> claims.claims(c -> c.remove("sub")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerAndLoginArePublic() throws Exception {
        var user = new UserResponse(UUID.fromString(USER_ID), "alex@example.invalid", "Alex", Instant.now());
        var response = new AuthResponse("token", "Bearer", Instant.now().plusSeconds(3600), user);
        given(authService.register(any())).willReturn(response);
        given(authService.login(any())).willReturn(response);

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"alex@example.invalid","password":"correct-horse-example","displayName":"Alex"}
                        """))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"alex@example.invalid","password":"correct-horse-example"}
                        """))
                .andExpect(status().isOk());
    }

    @Test
    void missingOrShortSecretFailsFast() {
        assertThatThrownBy(() -> SecurityConfig.requireStrongSecret(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SecurityConfig.requireStrongSecret("   "))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SecurityConfig.requireStrongSecret("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    /** Builds a valid token, then lets each test break one thing about it. */
    private static String bearer(String secret, Consumer<JwtClaimsSet.Builder> change) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("closecircle")
                .subject(USER_ID)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300));
        change.accept(claims);
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build())).getTokenValue();
    }
}
