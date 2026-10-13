package com.bready.server.plan.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bready.server.plan.api.PlanQueryApi;
import com.bready.server.plan.api.PlanSummary;
import com.bready.server.plan.domain.Plan;
import com.bready.server.plan.repository.PlanRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanQueryService implements PlanQueryApi {

    private final PlanRepository planRepository;

    @Override
    public long countActivePlans(Long ownerId) {
        return planRepository.countActiveByOwnerId(ownerId);
    }

    @Override
    public Optional<PlanSummary> findOwnedPlan(Long planId, Long ownerId) {
        return planRepository.findByIdAndOwnerId(planId, ownerId).map(this::toSummary);
    }

    @Override
    public List<PlanSummary> findRecentPlans(Long ownerId, int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by("planDate").descending());
        return planRepository.findAllByOwnerIdAndDeletedAtIsNull(ownerId, pageable).getContent().stream()
                .map(this::toSummary)
                .toList();
    }

    private PlanSummary toSummary(Plan plan) {
        return new PlanSummary(plan.getId(), plan.getTitle(), plan.getPlanDate(), plan.getRegion());
    }
}
