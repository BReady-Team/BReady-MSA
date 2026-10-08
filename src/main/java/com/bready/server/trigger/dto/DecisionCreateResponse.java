package com.bready.server.trigger.dto;

import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record DecisionCreateResponse(
        Long decisionId, String decisionType, LocalDateTime decidedAt, Boolean needSwitch // SWITCH일 때만 true
        ) {}
