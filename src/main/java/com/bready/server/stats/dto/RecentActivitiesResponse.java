package com.bready.server.stats.dto;

import java.util.List;

import com.bready.server.stats.domain.StatsPeriod;

import lombok.Builder;

@Builder
public record RecentActivitiesResponse(StatsPeriod period, int limit, List<RecentActivityItem> items) {}
