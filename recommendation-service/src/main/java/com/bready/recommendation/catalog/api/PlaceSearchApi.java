package com.bready.recommendation.catalog.api;

import java.util.List;

public interface PlaceSearchApi {

    // 결과가 없으면 예외가 아니라 빈 목록
    List<PlaceSearchResult> searchPlaces(
            PlaceCategoryType category, String keyword, Double latitude, Double longitude, Integer radius);
}
