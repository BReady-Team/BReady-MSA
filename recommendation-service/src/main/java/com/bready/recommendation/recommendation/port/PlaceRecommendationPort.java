package com.bready.recommendation.recommendation.port;

import java.util.List;

import com.bready.recommendation.catalog.api.PlaceCategoryType;
import com.bready.recommendation.planning.api.TriggerType;
import com.bready.recommendation.recommendation.dto.PlaceRecommendationResponse;

public interface PlaceRecommendationPort {

    List<PlaceRecommendationResponse.RecommendationItem> recommendPlaceCandidates(
            PlaceCategoryType categoryType,
            TriggerType triggerType,
            String region,
            Double latitude,
            Double longitude,
            int radius,
            int limit,
            String excludeExternalId);
}
