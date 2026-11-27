package com.bready.recommendation.recommendation.dto;

public record PlaceRecommendationQuery(
        String region, Double latitude, Double longitude, Integer radius, Integer size) {}
