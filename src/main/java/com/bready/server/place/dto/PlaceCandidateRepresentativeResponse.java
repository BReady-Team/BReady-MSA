package com.bready.server.place.dto;

import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record PlaceCandidateRepresentativeResponse(
        Long categoryId, Long representativeCandidateId, LocalDateTime changedAt) {}
