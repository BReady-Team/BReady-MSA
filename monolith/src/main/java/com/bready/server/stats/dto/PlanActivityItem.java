package com.bready.server.stats.dto;

import java.time.LocalDateTime;

import com.bready.server.trigger.api.DecisionType;
import com.bready.server.trigger.api.TriggerType;

import lombok.Builder;

@Builder
public record PlanActivityItem(
        String activityId, TriggerType triggerType, DecisionType decisionType, LocalDateTime createdAt) {}
