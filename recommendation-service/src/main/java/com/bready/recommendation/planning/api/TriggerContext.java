package com.bready.recommendation.planning.api;

import java.util.List;

public record TriggerContext(
        Long triggerId,
        TriggerType triggerType,
        String planRegion,
        CategoryInfo currentCategory,
        List<CategoryInfo> planCategories) {}
