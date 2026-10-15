package com.bready.catalog.place.domain;

import lombok.Getter;

@Getter
public enum PlaceCategoryType {
    MEAL("음식점", "FD6", true),
    CAFE("카페", "CE7", true),
    EXHIBITION("전시관", null, true),
    WALK("공원", "AT4", false),
    SHOPPING("쇼핑", null, true),
    REST("휴식", null, true);

    private final String keyword;
    private final String kakaoCategoryCode;
    private final boolean indoor;

    PlaceCategoryType(String keyword, String kakaoCategoryCode, boolean indoor) {
        this.keyword = keyword;
        this.kakaoCategoryCode = kakaoCategoryCode;
        this.indoor = indoor;
    }

    public String searchQuery(String userKeyword) {
        if (userKeyword == null || userKeyword.isBlank()) {
            return keyword;
        }
        return userKeyword + " " + keyword;
    }
}
