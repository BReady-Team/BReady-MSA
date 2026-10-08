package com.bready.server.recommendation.port;

import java.util.List;

import com.bready.server.plan.domain.PlanCategory;
import com.bready.server.recommendation.dto.CategoryRecommendationResponse;
import com.bready.server.trigger.domain.TriggerType;

public interface CategoryRecommendationPort {

    List<CategoryRecommendationResponse.CategoryItem> recommendCategories(
            List<PlanCategory> planCategories, PlanCategory currentCategory, TriggerType triggerType);
}
