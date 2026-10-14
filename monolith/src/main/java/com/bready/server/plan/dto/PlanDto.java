package com.bready.server.plan.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlanDto {

    private Long planId;
    private String title;
    private LocalDate planDate;
    private String region;
    private String status;
    private String ownerNickname;
    private String ownerProfileImageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
