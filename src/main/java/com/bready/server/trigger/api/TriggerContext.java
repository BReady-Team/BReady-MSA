package com.bready.server.trigger.api;

import java.util.List;

public record TriggerContext(
        Long triggerId,
        TriggerType triggerType,
        String planRegion,
        CategoryInfo currentCategory,
        List<CategoryInfo> planCategories) {}
