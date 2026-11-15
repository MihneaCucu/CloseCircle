package com.closecircle.api.location;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class LocationFreshnessPolicyTest {
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final String PROPERTY = "closecircle.location.recent-for-seconds";

    @Test
    void usesOneEvaluationTimeAndReportsTheConfiguredWindow() {
        var policy = new LocationFreshnessPolicy(300L, Clock.fixed(NOW, ZoneOffset.UTC));
        assertEquals(NOW, policy.now());
        assertEquals(new LocationFreshnessPolicy.Metadata(LocationFreshness.Status.RECENT, NOW, 300),
                policy.assess(NOW.minusSeconds(300), NOW));
        assertEquals(LocationFreshness.Status.LAST_KNOWN, policy.assess(NOW.minusSeconds(301), NOW).status());
        assertEquals(LocationFreshness.Status.UNKNOWN, policy.assess(null, NOW).status());
        assertEquals(LocationFreshness.Status.UNKNOWN, policy.assess(NOW.plusSeconds(1), NOW).status());
    }

    @Test
    void bindsExplicitConfigurationAndRejectsInvalidValues() {
        try (var context = context(Map.of(PROPERTY, "30"))) {
            context.refresh();
            assertEquals(30, context.getBean(LocationFreshnessPolicy.class).assess(NOW, NOW).recentForSeconds());
        }
        for (String invalid : new String[] {"-1", "", "bad", "1.5"}) {
            try (var context = context(Map.of(PROPERTY, invalid))) {
                assertThrows(BeansException.class, context::refresh);
            }
        }
    }

    @Test
    void omitsMetadataWhenNoWindowIsConfigured() {
        try (var context = context(Map.of())) {
            context.refresh();
            assertNull(context.getBean(LocationFreshnessPolicy.class).assess(NOW, NOW));
        }
    }

    @Test
    void supportsTheDocumentedEnvironmentVariable() {
        try (var context = context(Map.of())) {
            context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                    "test-systemEnvironment", Map.of("CLOSECIRCLE_LOCATION_RECENTFORSECONDS", "60")));
            ConfigurationPropertySources.attach(context.getEnvironment());
            context.refresh();
            assertEquals(60, context.getBean(LocationFreshnessPolicy.class).assess(NOW, NOW).recentForSeconds());
        }
    }

    private AnnotationConfigApplicationContext context(Map<String, Object> properties) {
        var context = new AnnotationConfigApplicationContext();
        var sources = context.getEnvironment().getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.addFirst(new MapPropertySource("test", properties));
        context.register(LocationFreshnessPolicy.class);
        return context;
    }
}
