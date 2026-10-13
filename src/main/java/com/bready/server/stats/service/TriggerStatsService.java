package com.bready.server.stats.service;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bready.server.stats.domain.StatsPeriod;
import com.bready.server.stats.dto.TriggerStatsItem;
import com.bready.server.stats.dto.TriggerStatsResponse;
import com.bready.server.trigger.api.ActivityQueryApi;
import com.bready.server.trigger.api.TriggerType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TriggerStatsService {

    private final ActivityQueryApi activityQueryApi;

    public TriggerStatsResponse getTriggerStats(Long userId, StatsPeriod period) {

        LocalDateTime startAt = resolveStartAt(period);

        long totalCount = activityQueryApi.countTriggers(userId, startAt);

        Map<TriggerType, Long> countsByType = activityQueryApi.countTriggersByType(userId, startAt);

        List<TriggerStatsItem> items = Arrays.stream(TriggerType.values())
                .map(type -> {
                    long count = countsByType.getOrDefault(type, 0L);
                    int percentage = totalCount == 0 ? 0 : (int) Math.round((double) count * 100 / totalCount);

                    return TriggerStatsItem.builder()
                            .triggerType(type)
                            .count(count)
                            .percentage(percentage)
                            .build();
                })
                .toList();

        return TriggerStatsResponse.builder()
                .period(period)
                .totalCount(totalCount)
                .items(items)
                .build();
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
