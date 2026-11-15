package com.closecircle.api.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class LocationAccuracyConfigurationTest {
    private static final String PROPERTY = "closecircle.location.poor-accuracy-threshold-m";

    @Test
    void doesNotInventAThresholdWhenConfigurationIsAbsent() {
        try (var context = context(Map.of())) {
            context.refresh();
            assertTrue(context.getBeansOfType(LocationAccuracy.class).isEmpty());
        }
    }

    @Test
    void usesTheConfiguredThresholdInMetres() {
        try (var context = context(Map.of(PROPERTY, "25.5"))) {
            context.refresh();
            LocationAccuracy policy = context.getBean(LocationAccuracy.class);
            assertEquals(LocationAccuracy.Status.ACCEPTABLE, policy.assess(25.5));
            assertEquals(LocationAccuracy.Status.POOR, policy.assess(25.6));
        }
    }

    @Test
    void invalidConfigurationFailsAtStartup() {
        for (String invalid : new String[] {"0", "-1", "NaN", "Infinity", "", "bad", "false"}) {
            try (var context = context(Map.of(PROPERTY, invalid))) {
                assertThrows(BeansException.class, context::refresh);
            }
        }
    }

    @Test
    void supportsTheDocumentedEnvironmentVariable() {
        try (var context = context(Map.of())) {
            context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                    "test-systemEnvironment", Map.of("CLOSECIRCLE_LOCATION_POORACCURACYTHRESHOLDM", "40")));
            ConfigurationPropertySources.attach(context.getEnvironment());
            context.refresh();
            LocationAccuracy policy = context.getBean(LocationAccuracy.class);
            assertEquals(LocationAccuracy.Status.ACCEPTABLE, policy.assess(40.0));
            assertEquals(LocationAccuracy.Status.POOR, policy.assess(40.1));
        }
    }

    private AnnotationConfigApplicationContext context(Map<String, Object> properties) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(LocationAccuracyConfiguration.class);
        return context;
    }
}
