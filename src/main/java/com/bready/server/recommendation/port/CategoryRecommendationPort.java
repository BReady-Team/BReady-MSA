package com.bready.server.recommendation.port;

import java.util.List;

import com.bready.server.recommendation.dto.CategoryRecommendationResponse;
import com.bready.server.trigger.api.CategoryInfo;
import com.bready.server.trigger.api.TriggerType;

public interface CategoryRecommendationPort {

    List<CategoryRecommendationResponse.CategoryItem> recommendCategories(
            List<CategoryInfo> planCategories, CategoryInfo currentCategory, TriggerType triggerType);
}
