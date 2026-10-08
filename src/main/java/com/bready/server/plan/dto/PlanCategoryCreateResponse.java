package com.bready.server.plan.dto;

import java.time.LocalDateTime;

import com.bready.server.place.domain.PlaceCategoryType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlanCategoryCreateResponse {

    private Long planCategoryId;
    private Long planId;
    private PlaceCategoryType categoryType;
    private Integer sequence;
    private LocalDateTime createdAt;
}
