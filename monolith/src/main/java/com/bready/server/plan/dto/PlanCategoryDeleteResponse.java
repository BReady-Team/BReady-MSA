package com.bready.server.plan.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlanCategoryDeleteResponse {

    private Long planId;
    private Long planCategoryId;
    private LocalDateTime deletedAt;
}
