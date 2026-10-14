package com.bready.server.stats.dto;

import java.util.List;

import lombok.Builder;

@Builder
public record PlanActivitiesResponse(Long planId, String planTitle, int limit, List<PlanActivityItem> items) {}
