package com.bready.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

import io.micrometer.observation.ObservationPredicate;

@Configuration
public class ObservationConfig {

    @Bean
    public ObservationPredicate skipActuatorObservations() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext serverContext
                && serverContext.getCarrier().getPath().value().startsWith("/actuator"));
    }
}
