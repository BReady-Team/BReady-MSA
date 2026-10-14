package com.bready.server.recommendation.port;

import java.util.List;

import com.bready.server.place.api.PlaceCategoryType;
import com.bready.server.recommendation.dto.PlaceRecommendationResponse;
import com.bready.server.trigger.api.TriggerType;

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
