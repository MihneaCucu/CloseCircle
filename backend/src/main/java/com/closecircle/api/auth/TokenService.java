package com.closecircle.api.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.closecircle.api.config.JwtProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues signed access tokens. Validation happens in SecurityConfig's JwtDecoder. */
@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public TokenService(JwtEncoder encoder, JwtProperties properties) {
        this(encoder, properties, Clock.systemUTC());
    }

    /** Lets tests control the current time. */
    TokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.ttl = properties.accessTokenTtl();
        this.clock = clock;
    }

    public IssuedToken issue(UUID userId) {
        // JWT times are whole seconds; truncating keeps expiresAt in the response equal to the token's exp.
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(ttl);
        var claims = JwtClaimsSet.builder()
                .issuer(JwtProperties.ISSUER)
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        // The header must name HS256 explicitly; the encoder would otherwise assume RS256.
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedToken[value=***, expiresAt=" + expiresAt + "]";
        }
    }
}
