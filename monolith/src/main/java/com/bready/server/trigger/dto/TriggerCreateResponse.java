package com.bready.server.trigger.dto;

import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record TriggerCreateResponse(Long triggerId, LocalDateTime occurredAt) {}
