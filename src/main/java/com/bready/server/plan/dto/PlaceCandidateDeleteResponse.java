package com.bready.server.plan.dto;

import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record PlaceCandidateDeleteResponse(Long candidateId, LocalDateTime deletedAt) {}
