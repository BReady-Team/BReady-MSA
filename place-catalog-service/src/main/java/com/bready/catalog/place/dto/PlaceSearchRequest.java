package com.bready.catalog.place.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.bready.catalog.place.domain.PlaceCategoryType;

public record PlaceSearchRequest(
        @NotNull(message = "카테고리를 선택해주세요.") PlaceCategoryType category,
        String keyword,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Min(0) @Max(MAX_RADIUS) Integer radius) {

    public static final int DEFAULT_RADIUS = 2000;
    public static final int MAX_RADIUS = 10000;

    public PlaceSearchRequest {
        radius = radius == null ? DEFAULT_RADIUS : radius;
    }
}
