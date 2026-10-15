package com.bready.catalog.place.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceCategoryTypeTest {

    @Test
    @DisplayName("성공 - 사용자 검색어가 있으면 검색어 뒤에 카테고리 키워드를 붙인다")
    void searchQuery_withUserKeyword() {
        String query = PlaceCategoryType.CAFE.searchQuery("성수");

        assertThat(query).isEqualTo("성수 카페");
    }

    @Test
    @DisplayName("성공 - 사용자 검색어가 비면 카테고리 키워드만 쓴다")
    void searchQuery_blankUserKeyword() {
        String query = PlaceCategoryType.WALK.searchQuery("  ");

        assertThat(query).isEqualTo("공원");
    }

    @Test
    @DisplayName("성공 - 사용자 검색어가 없으면 카테고리 키워드만 쓴다")
    void searchQuery_nullUserKeyword() {
        String query = PlaceCategoryType.MEAL.searchQuery(null);

        assertThat(query).isEqualTo("음식점");
    }
}
