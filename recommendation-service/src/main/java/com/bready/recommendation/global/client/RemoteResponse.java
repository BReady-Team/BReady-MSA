package com.bready.recommendation.global.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteResponse<T>(Integer errorCode, String message, T data) {}
