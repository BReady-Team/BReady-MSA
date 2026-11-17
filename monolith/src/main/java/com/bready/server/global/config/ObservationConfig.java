package com.bready.server.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import io.micrometer.observation.ObservationPredicate;

@Configuration
public class ObservationConfig {

    @Bean
    public ObservationPredicate skipActuatorObservations() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext serverContext
                && serverContext.getCarrier().getRequestURI().startsWith("/actuator"));
    }

    @Bean
    public ObservationPredicate skipSecurityFilterObservations() {
        return (name, context) -> !name.startsWith("spring.security.");
    }
}
