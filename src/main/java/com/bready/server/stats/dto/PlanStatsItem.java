package com.bready.server.stats.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;

@Builder
public record PlanStatsItem(
        Long planId,
        String planTitle,
        LocalDate planDate,
        String region,
        List<String> categoryTypes,
        Long totalSwitches) {}
