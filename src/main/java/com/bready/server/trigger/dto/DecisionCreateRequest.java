package com.bready.server.trigger.dto;

import jakarta.validation.constraints.NotNull;

import com.bready.server.trigger.domain.DecisionType;

public record DecisionCreateRequest(@NotNull DecisionType decisionType) {}
