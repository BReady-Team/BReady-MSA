package com.bready.server.trigger.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bready.server.trigger.api.ActivityQueryApi;
import com.bready.server.trigger.api.DecisionType;
import com.bready.server.trigger.api.PlanActivity;
import com.bready.server.trigger.api.RecentActivity;
import com.bready.server.trigger.api.TriggerType;
import com.bready.server.trigger.repository.DecisionRepository;
import com.bready.server.trigger.repository.SwitchLogRepository;
import com.bready.server.trigger.repository.TriggerRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityQueryService implements ActivityQueryApi {

    private final TriggerRepository triggerRepository;
    private final DecisionRepository decisionRepository;
    private final SwitchLogRepository switchLogRepository;

    @Override
    public long countSwitches(Long ownerId, LocalDateTime startAt) {
        return switchLogRepository.countByOwnerIdAndPeriod(ownerId, startAt);
    }

    @Override
    public long countDecisions(Long ownerId, LocalDateTime startAt) {
        return decisionRepository.countByOwnerIdAndPeriod(ownerId, startAt);
    }

    @Override
    public long countTriggers(Long ownerId, LocalDateTime startAt) {
        return triggerRepository.countByOwnerIdAndPeriod(ownerId, startAt);
    }

    @Override
    public Map<TriggerType, Long> countTriggersByType(Long ownerId, LocalDateTime startAt) {
        return triggerRepository.countByTriggerType(ownerId, startAt).stream()
                .collect(Collectors.toMap(
                        TriggerRepository.TriggerTypeCount::getTriggerType,
                        TriggerRepository.TriggerTypeCount::getCount));
    }

    @Override
    public List<RecentActivity> findRecentSwitches(Long ownerId, LocalDateTime startAt, int limit) {
        return switchLogRepository
                .findRecentSwitchActivitiesAllPlans(ownerId, startAt, PageRequest.of(0, limit))
                .stream()
                .map(row -> new RecentActivity(
                        row.getLogId(), row.getPlanId(), row.getPlanTitle(), row.getTriggerType(), row.getCreatedAt()))
                .toList();
    }

    @Override
    public List<RecentActivity> findRecentKeeps(Long ownerId, LocalDateTime startAt, int limit) {
        return decisionRepository.findRecentKeepActivities(ownerId, startAt, PageRequest.of(0, limit)).stream()
                .map(row -> new RecentActivity(
                        row.getDecisionId(),
                        row.getPlanId(),
                        row.getPlanTitle(),
                        row.getTriggerType(),
                        row.getCreatedAt()))
                .toList();
    }

    @Override
    public List<PlanActivity> findRecentSwitchesOfPlan(Long ownerId, Long planId, int limit) {
        return switchLogRepository.findRecentSwitchActivities(ownerId, planId, PageRequest.of(0, limit)).stream()
                .map(row -> new PlanActivity(row.getLogId(), row.getTriggerType(), row.getCreatedAt()))
                .toList();
    }

    @Override
    public List<PlanActivity> findRecentKeepsOfPlan(Long ownerId, Long planId, int limit) {
        return decisionRepository
                .findRecentKeepDecisionActivities(ownerId, planId, DecisionType.KEEP, PageRequest.of(0, limit))
                .stream()
                .map(row -> new PlanActivity(row.getLogId(), row.getTriggerType(), row.getCreatedAt()))
                .toList();
    }

    @Override
    public long countTriggersOfPlan(Long planId, LocalDateTime startAt) {
        return triggerRepository.countByPlanIdAndPeriod(planId, startAt);
    }

    @Override
    public long countSwitchesOfPlan(Long planId, LocalDateTime startAt) {
        return switchLogRepository.countSwitchByPlanIdAndPeriod(planId, startAt);
    }
}
