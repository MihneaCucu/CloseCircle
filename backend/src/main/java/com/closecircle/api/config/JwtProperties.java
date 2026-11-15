package com.closecircle.api.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings under {@code closecircle.jwt} in application.yml. */
@ConfigurationProperties("closecircle.jwt")
public record JwtProperties(String secret, @DefaultValue("1h") Duration accessTokenTtl) {
    /** Every token we issue carries this issuer, and the decoder rejects any other. */
    public static final String ISSUER = "closecircle";

    public JwtProperties {
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalStateException("closecircle.jwt.access-token-ttl must be a positive duration");
        }
    }

    /** Keeps the signing secret out of logs if this object is ever printed. */
    @Override
    public String toString() {
        return "JwtProperties[secret=***, accessTokenTtl=" + accessTokenTtl + "]";
    }
}
