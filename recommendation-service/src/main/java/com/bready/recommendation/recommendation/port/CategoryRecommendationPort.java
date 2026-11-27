package com.bready.recommendation.recommendation.port;

import java.util.List;

import com.bready.recommendation.planning.api.CategoryInfo;
import com.bready.recommendation.planning.api.TriggerType;
import com.bready.recommendation.recommendation.dto.CategoryRecommendationResponse;

public interface CategoryRecommendationPort {

    List<CategoryRecommendationResponse.CategoryItem> recommendCategories(
            List<CategoryInfo> planCategories, CategoryInfo currentCategory, TriggerType triggerType);
}
