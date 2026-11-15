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
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenServiceTest {
    static final String SECRET = "test-only-secret-that-is-at-least-32-bytes-long";
    static final SecretKeySpec KEY = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    static final Instant ISSUED = Instant.parse("2026-11-15T10:00:00.750Z");

    @Test
    void issuedTokenCarriesUserIdIssuerAndExpiry() {
        UUID userId = UUID.randomUUID();

        TokenService.IssuedToken token = serviceAt(ISSUED).issue(userId);
        Jwt jwt = decoderAt(ISSUED).decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        // getIssuer() would try to parse the issuer as a URL; ours is a plain name.
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("closecircle");
        assertThat(jwt.getIssuedAt()).isEqualTo(Instant.parse("2026-11-15T10:00:00Z"));
        assertThat(jwt.getExpiresAt()).isEqualTo(Instant.parse("2026-11-15T11:00:00Z"));
        assertThat(token.expiresAt()).isEqualTo(jwt.getExpiresAt());
    }

    @Test
    void tokenIsRejectedOnceItsLifetimeHasPassed() {
        String token = serviceAt(ISSUED).issue(UUID.randomUUID()).value();

        // Still valid one second before expiry...
        assertThat(decoderAt(Instant.parse("2026-11-15T10:59:59Z")).decode(token)).isNotNull();
        // ...and rejected after it. The validator allows 60 seconds of clock skew, hence 11:01:01.
        assertThatThrownBy(() -> decoderAt(Instant.parse("2026-11-15T11:01:01Z")).decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void nonPositiveLifetimeIsRejectedAtStartup() {
        assertThatThrownBy(() -> new JwtProperties(SECRET, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
    }

    private static TokenService serviceAt(Instant now) {
        return new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(KEY)),
                new JwtProperties(SECRET, Duration.ofHours(1)), Clock.fixed(now, ZoneOffset.UTC));
    }

    /**
     * A decoder whose expiry check uses a frozen clock. With the real clock, a token issued at the
     * fixed test time would count as expired as soon as that time had passed, and the test would
     * start failing by itself.
     */
    private static NimbusJwtDecoder decoderAt(Instant now) {
        var decoder = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
        var timestamps = new JwtTimestampValidator();
        timestamps.setClock(Clock.fixed(now, ZoneOffset.UTC));
        decoder.setJwtValidator(timestamps);
        return decoder;
    }
}
