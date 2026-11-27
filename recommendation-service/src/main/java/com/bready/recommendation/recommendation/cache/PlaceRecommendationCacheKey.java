package com.bready.recommendation.recommendation.cache;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.bready.recommendation.catalog.api.PlaceCategoryType;
import com.bready.recommendation.planning.api.TriggerType;

// 추천 결과를 바꾸는 입력 전부 -> 하나라도 다르면 다른 캐시
public record PlaceRecommendationCacheKey(
        PlaceCategoryType categoryType,
        TriggerType triggerType,
        String region,
        Double latitude,
        Double longitude,
        int radius,
        int limit,
        String excludeExternalId) {

    private static final String PREFIX = "reco:place:v2";
    private static final String NONE = "-";
    private static final int COORDINATE_SCALE = 2;

    public String toRedisKey() {
        return String.join(
                ":",
                PREFIX,
                categoryType.name(),
                triggerType.name(),
                orNone(region),
                round(latitude),
                round(longitude),
                String.valueOf(radius),
                String.valueOf(limit),
                orNone(excludeExternalId));
    }

    // 소수 둘째 자리까지 같은 위치는 같은 캐시를 쓴다
    private static String round(Double coordinate) {
        if (coordinate == null) {
            return NONE;
        }
        return BigDecimal.valueOf(coordinate)
                .setScale(COORDINATE_SCALE, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static String orNone(String value) {
        return value == null ? NONE : value;
    }
}
