package com.bready.server.trigger.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bready.server.trigger.api.ActivityQueryApi;
import com.bready.server.trigger.repository.DecisionRepository;
import com.bready.server.trigger.repository.SwitchLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityQueryService implements ActivityQueryApi {

    private final SwitchLogRepository switchLogRepository;
    private final DecisionRepository decisionRepository;

    @Override
    public long countSwitches(Long ownerId, LocalDateTime startAt) {
        return switchLogRepository.countByOwnerIdAndPeriod(ownerId, startAt);
    }

    @Override
    public long countDecisions(Long ownerId, LocalDateTime startAt) {
        return decisionRepository.countByOwnerIdAndPeriod(ownerId, startAt);
    }
}
