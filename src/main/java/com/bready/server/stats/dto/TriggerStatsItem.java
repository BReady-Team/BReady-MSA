package com.bready.server.stats.dto;

import com.bready.server.trigger.api.TriggerType;

import lombok.Builder;

@Builder
public record TriggerStatsItem(TriggerType triggerType, long count, int percentage) {}
