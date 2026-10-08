package com.bready.server.recommendation.dto;

import java.util.List;

import com.bready.server.place.domain.PlaceCategoryType;

public record CategoryRecommendationResponse(List<CategoryItem> items) {
    public record CategoryItem(PlaceCategoryType categoryType, String label, String reason) {}
}
