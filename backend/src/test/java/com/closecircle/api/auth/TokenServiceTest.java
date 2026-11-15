package com.closecircle.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;

import com.closecircle.api.config.JwtProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenServiceTest {
    static final String SECRET = "test-only-secret-that-is-at-least-32-bytes-long";
    static final SecretKeySpec KEY = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    @Test
    void issuedTokenCarriesUserIdIssuerAndExpiry() {
        Instant now = Instant.parse("2026-11-15T10:00:00.750Z");
        var service = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(KEY)),
                new JwtProperties(SECRET, Duration.ofHours(1)), Clock.fixed(now, ZoneOffset.UTC));
        UUID userId = UUID.randomUUID();

        TokenService.IssuedToken token = service.issue(userId);
        Jwt jwt = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build()
                .decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        // getIssuer() would try to parse the issuer as a URL; ours is a plain name.
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("closecircle");
        assertThat(jwt.getIssuedAt()).isEqualTo(Instant.parse("2026-11-15T10:00:00Z"));
        assertThat(jwt.getExpiresAt()).isEqualTo(Instant.parse("2026-11-15T11:00:00Z"));
        assertThat(token.expiresAt()).isEqualTo(jwt.getExpiresAt());
    }

    @Test
    void nonPositiveLifetimeIsRejectedAtStartup() {
        assertThatThrownBy(() -> new JwtProperties(SECRET, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
    }
}
