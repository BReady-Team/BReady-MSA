package com.bready.catalog.place.external;

public record KakaoKeywordSearchCondition(
        String query, Double latitude, Double longitude, Integer radius, String categoryGroupCode) {

    public boolean hasCoordinate() {
        return latitude != null && longitude != null;
    }
}
