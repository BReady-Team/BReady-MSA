package com.bready.server.plan.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlanListItemDto {

    private Long planId;
    private String title;
    private LocalDate planDate;
    private String region;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
