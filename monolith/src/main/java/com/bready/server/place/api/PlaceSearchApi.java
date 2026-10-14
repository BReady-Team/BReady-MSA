package com.bready.server.place.api;

import java.util.List;

// Place Catalog가 다른 컨텍스트에 여는 창구 (외부 장소 검색)
public interface PlaceSearchApi {

    // 결과가 없으면 예외가 아니라 빈 목록을 돌려줌
    List<PlaceSearchResult> searchPlaces(
            PlaceCategoryType category, String keyword, Double latitude, Double longitude, Integer radius);
}
