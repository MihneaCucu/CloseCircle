package com.closecircle.api.location;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration(proxyBeanMethods = false)
public class LocationAccuracyConfiguration {
    @Bean
    @Conditional(ThresholdConfigured.class)
    LocationAccuracy locationAccuracy(
            @Value("${closecircle.location.poor-accuracy-threshold-m}") double thresholdM) {
        return new LocationAccuracy(thresholdM);
    }

    static class ThresholdConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return context.getEnvironment().containsProperty("closecircle.location.poor-accuracy-threshold-m");
        }
    }
}
