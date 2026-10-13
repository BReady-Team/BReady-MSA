package com.bready.server.trigger.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// Planning이 다른 컨텍스트에 여는 조회 창구 (트리거·결정·전환 기록)
public interface ActivityQueryApi {

    long countSwitches(Long ownerId, LocalDateTime startAt);

    long countDecisions(Long ownerId, LocalDateTime startAt);

    long countTriggers(Long ownerId, LocalDateTime startAt);

    Map<TriggerType, Long> countTriggersByType(Long ownerId, LocalDateTime startAt);

    List<RecentActivity> findRecentSwitches(Long ownerId, LocalDateTime startAt, int limit);

    List<RecentActivity> findRecentKeeps(Long ownerId, LocalDateTime startAt, int limit);

    List<PlanActivity> findRecentSwitchesOfPlan(Long ownerId, Long planId, int limit);

    List<PlanActivity> findRecentKeepsOfPlan(Long ownerId, Long planId, int limit);

    long countTriggersOfPlan(Long planId, LocalDateTime startAt);

    long countSwitchesOfPlan(Long planId, LocalDateTime startAt);
}
