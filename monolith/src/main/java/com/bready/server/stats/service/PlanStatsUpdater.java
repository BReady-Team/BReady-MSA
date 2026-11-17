package com.bready.server.stats.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bready.server.stats.domain.PlanStats;
import com.bready.server.stats.domain.StatsPeriod;
import com.bready.server.stats.repository.PlanStatsRepository;
import com.bready.server.trigger.api.ActivityQueryApi;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PlanStatsUpdater {

    private final PlanStatsRepository planStatsRepository;
    private final ActivityQueryApi activityQueryApi;
    private final Clock clock;

    @Transactional
    public void recalculate(Long planId, StatsPeriod period) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime from = resolveFrom(period, now);

        long triggerCount = activityQueryApi.countTriggersOfPlan(planId, from);

        long switchCount = activityQueryApi.countSwitchesOfPlan(planId, from);

        BigDecimal reliability = calculateReliability(triggerCount, switchCount);

        // 행을 먼저 보장한 뒤 잠근다
        planStatsRepository.insertIfAbsent(planId, period.name(), now);
        PlanStats stats = planStatsRepository
                .findByPlanIdAndPeriodForUpdate(planId, period)
                .orElseThrow(() -> new IllegalStateException("방금 보장한 통계 행이 없습니다. planId=" + planId));

        stats.update((int) triggerCount, (int) switchCount, reliability);
    }

    private LocalDateTime resolveFrom(StatsPeriod period, LocalDateTime now) {
        return switch (period) {
            case WEEK -> now.minusWeeks(1);
            case MONTH -> now.minusMonths(1);
            case ALL -> null;
        };
    }

    private BigDecimal calculateReliability(long triggerCount, long switchCount) {
        if (triggerCount == 0) {
            return BigDecimal.valueOf(100);
        }

        double ratio = Math.max(0, 1 - ((double) switchCount / triggerCount));

        return BigDecimal.valueOf(ratio * 100).setScale(2, RoundingMode.HALF_UP);
    }
}
