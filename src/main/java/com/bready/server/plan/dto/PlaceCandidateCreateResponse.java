package com.bready.server.plan.dto;

import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record PlaceCandidateCreateResponse(Long candidateId, PlaceSummaryResponse place, LocalDateTime createdAt) {}
