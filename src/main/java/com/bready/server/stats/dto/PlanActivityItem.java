package com.bready.server.stats.dto;

import java.time.LocalDateTime;

import com.bready.server.trigger.domain.DecisionType;
import com.bready.server.trigger.domain.TriggerType;

import lombok.Builder;

@Builder
public record PlanActivityItem(
        String activityId, TriggerType triggerType, DecisionType decisionType, LocalDateTime createdAt) {}
