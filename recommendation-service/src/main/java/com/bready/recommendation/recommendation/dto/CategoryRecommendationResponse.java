package com.bready.recommendation.recommendation.dto;

import java.util.List;

import com.bready.recommendation.catalog.api.PlaceCategoryType;

public record CategoryRecommendationResponse(List<CategoryItem> items) {
    public record CategoryItem(PlaceCategoryType categoryType, String label, String reason) {}
}
