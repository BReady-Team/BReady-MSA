package com.bready.server.stats.dto;

import java.time.LocalDateTime;

import com.bready.server.trigger.domain.DecisionType;
import com.bready.server.trigger.domain.TriggerType;

import lombok.Builder;

@Builder
public record RecentActivityItem(
        String activityId,
        Long planId,
        String planTitle,
        TriggerType triggerType,
        DecisionType decisionType,
        LocalDateTime createdAt) {}
