package com.bready.server.stats.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.stats.domain.StatsPeriod;
import com.bready.server.stats.dto.RecentActivitiesResponse;
import com.bready.server.stats.dto.RecentActivityItem;
import com.bready.server.stats.exception.StatsErrorCase;
import com.bready.server.trigger.api.ActivityQueryApi;
import com.bready.server.trigger.api.DecisionType;
import com.bready.server.trigger.api.RecentActivity;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecentActivityStatsService {

    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 20;

    private final ActivityQueryApi activityQueryApi;

    public RecentActivitiesResponse getRecentActivities(Long ownerId, StatsPeriod period, Integer limitParam) {
        int limit = normalizeLimit(limitParam);
        LocalDateTime startAt = resolveStartAt(period);

        // 전환과 유지를 합쳐 최신 limit건을 고른다.
        int fetchSize = limit * 2;

        List<RecentActivity> switches = activityQueryApi.findRecentSwitches(ownerId, startAt, fetchSize);

        List<RecentActivity> keeps = activityQueryApi.findRecentKeeps(ownerId, startAt, fetchSize);

        List<RecentActivityItem> merged = new ArrayList<>();

        for (RecentActivity activity : switches) {
            merged.add(RecentActivityItem.builder()
                    .activityId("SWITCH-" + activity.id())
                    .planId(activity.planId())
                    .planTitle(activity.planTitle())
                    .triggerType(activity.triggerType())
                    .decisionType(DecisionType.SWITCH)
                    .createdAt(activity.createdAt())
                    .build());
        }

        for (RecentActivity activity : keeps) {
            merged.add(RecentActivityItem.builder()
                    .activityId("KEEP-" + activity.id())
                    .planId(activity.planId())
                    .planTitle(activity.planTitle())
                    .triggerType(activity.triggerType())
                    .decisionType(DecisionType.KEEP)
                    .createdAt(activity.createdAt())
                    .build());
        }

        List<RecentActivityItem> items = merged.stream()
                .sorted(Comparator.comparing(RecentActivityItem::createdAt).reversed())
                .limit(limit)
                .toList();

        return RecentActivitiesResponse.builder()
                .period(period)
                .limit(limit)
                .items(items)
                .build();
    }

    private int normalizeLimit(Integer limitParam) {
        int limit = (limitParam == null) ? DEFAULT_LIMIT : limitParam;
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw ApplicationException.from(StatsErrorCase.INVALID_LIMIT);
        }
        return limit;
    }

    private LocalDateTime resolveStartAt(StatsPeriod period) {
        LocalDateTime now = LocalDateTime.now();
        return switch (period) {
            case WEEK -> now.minusWeeks(1);
            case MONTH -> now.minusMonths(1);
            case ALL -> null;
        };
    }
}
