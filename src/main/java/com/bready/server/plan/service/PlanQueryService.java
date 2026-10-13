package com.bready.server.plan.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bready.server.plan.api.PlanQueryApi;
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
}
