package com.bready.server.stats.dto;

import java.util.List;

import com.bready.server.stats.domain.StatsPeriod;

import lombok.Builder;

@Builder
public record PlanStatsResponse(StatsPeriod period, List<PlanStatsItem> items) {
    public static PlanStatsResponse empty(StatsPeriod period) {
        return PlanStatsResponse.builder().period(period).items(List.of()).build();
    }
}
