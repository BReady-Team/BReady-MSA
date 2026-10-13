package com.bready.server.stats.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.plan.api.PlanQueryApi;
import com.bready.server.plan.api.PlanSummary;
import com.bready.server.stats.dto.PlanActivitiesResponse;
import com.bready.server.stats.dto.PlanActivityItem;
import com.bready.server.stats.exception.StatsErrorCase;
import com.bready.server.trigger.api.ActivityQueryApi;
import com.bready.server.trigger.api.DecisionType;
import com.bready.server.trigger.api.PlanActivity;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanActivityStatsService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;

    private final PlanQueryApi planQueryApi;
    private final ActivityQueryApi activityQueryApi;

    public PlanActivitiesResponse getPlanActivities(Long ownerId, Long planId, Integer limitParam) {

        int limit = normalizeLimit(limitParam);

        PlanSummary plan = planQueryApi
                .findOwnedPlan(planId, ownerId)
                .orElseThrow(() -> ApplicationException.from(StatsErrorCase.INVALID_PARAMETER));

        // 전환과 유지를 합쳐 최신 limit건을 고른다.
        int fetchSize = limit * 2;

        List<PlanActivity> switches = activityQueryApi.findRecentSwitchesOfPlan(ownerId, planId, fetchSize);

        List<PlanActivity> keeps = activityQueryApi.findRecentKeepsOfPlan(ownerId, planId, fetchSize);

        List<PlanActivityItem> merged = new ArrayList<>();

        for (PlanActivity activity : switches) {
            merged.add(PlanActivityItem.builder()
                    .activityId("SWITCH-" + activity.id())
                    .triggerType(activity.triggerType())
                    .decisionType(DecisionType.SWITCH)
                    .createdAt(activity.createdAt())
                    .build());
        }

        for (PlanActivity activity : keeps) {
            merged.add(PlanActivityItem.builder()
                    .activityId("KEEP-" + activity.id())
                    .triggerType(activity.triggerType())
                    .decisionType(DecisionType.KEEP)
                    .createdAt(activity.createdAt())
                    .build());
        }

        List<PlanActivityItem> items = merged.stream()
                .sorted(Comparator.comparing(PlanActivityItem::createdAt).reversed())
                .limit(limit)
                .toList();

        return PlanActivitiesResponse.builder()
                .planId(plan.planId())
                .planTitle(plan.title())
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
}
