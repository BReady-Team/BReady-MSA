package com.bready.server.place.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaceCatalogResponse<T>(Integer errorCode, String message, T data) {}
