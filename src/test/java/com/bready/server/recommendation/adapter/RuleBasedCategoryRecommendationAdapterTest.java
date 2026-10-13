package com.bready.server.recommendation.adapter;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bready.server.place.api.PlaceCategoryType;
import com.bready.server.recommendation.dto.CategoryRecommendationResponse;
import com.bready.server.trigger.api.CategoryInfo;
import com.bready.server.trigger.api.TriggerType;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedCategoryRecommendationAdapterTest {

    private static final CategoryInfo MEAL = new CategoryInfo(1L, PlaceCategoryType.MEAL, 1);
    private static final CategoryInfo CAFE = new CategoryInfo(2L, PlaceCategoryType.CAFE, 2);
    private static final CategoryInfo WALK = new CategoryInfo(3L, PlaceCategoryType.WALK, 3);

    private final RuleBasedCategoryRecommendationAdapter adapter = new RuleBasedCategoryRecommendationAdapter();

    @Test
    @DisplayName("성공 - 현재 카테고리와 앞뒤 카테고리의 유형은 추천에서 뺀다")
    void recommendCategories_excludesCurrentAndNeighbors() {
        List<CategoryRecommendationResponse.CategoryItem> items =
                adapter.recommendCategories(List.of(MEAL, CAFE, WALK), CAFE, TriggerType.WAITING_TOO_LONG);

        assertThat(items)
                .extracting(CategoryRecommendationResponse.CategoryItem::categoryType)
                .containsExactly(PlaceCategoryType.EXHIBITION, PlaceCategoryType.SHOPPING, PlaceCategoryType.REST);
    }

    @Test
    @DisplayName("성공 - 날씨 악화 트리거면 실외 유형(산책)은 추천하지 않는다")
    void recommendCategories_weatherBadExcludesOutdoor() {
        List<CategoryRecommendationResponse.CategoryItem> items =
                adapter.recommendCategories(List.of(CAFE), CAFE, TriggerType.WEATHER_BAD);

        assertThat(items)
                .extracting(CategoryRecommendationResponse.CategoryItem::categoryType)
                .doesNotContain(PlaceCategoryType.WALK, PlaceCategoryType.CAFE)
                .contains(PlaceCategoryType.MEAL);
    }
}
