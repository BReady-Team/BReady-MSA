package com.bready.server.trigger.dto;

import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record DecisionSwitchResponse(
        Long switchLogId, Long fromCandidateId, Long toCandidateId, LocalDateTime switchedAt) {}
