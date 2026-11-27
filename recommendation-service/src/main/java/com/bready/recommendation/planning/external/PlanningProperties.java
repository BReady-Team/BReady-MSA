package com.bready.recommendation.planning.external;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "planning")
public record PlanningProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {}
