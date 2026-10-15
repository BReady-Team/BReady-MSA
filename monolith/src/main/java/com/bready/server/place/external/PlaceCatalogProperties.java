package com.bready.server.place.external;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "place-catalog")
public record PlaceCatalogProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {}
