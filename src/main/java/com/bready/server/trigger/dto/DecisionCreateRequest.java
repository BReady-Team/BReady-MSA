package com.bready.server.trigger.dto;

import jakarta.validation.constraints.NotNull;

import com.bready.server.trigger.api.DecisionType;

public record DecisionCreateRequest(@NotNull DecisionType decisionType) {}
