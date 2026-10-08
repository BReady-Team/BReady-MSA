package com.bready.server.trigger.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.bready.server.trigger.domain.TriggerType;

public record TriggerCreateRequest(
        @NotNull @Positive Long planId, @NotNull @Positive Long categoryId, @NotNull TriggerType triggerType) {}
