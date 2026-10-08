package com.bready.server.plan.dto;

import java.time.LocalDateTime;

import com.bready.server.place.domain.PlaceCategoryType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlanCategoryTypeUpdateResponse {
    private Long planId;
    private Long planCategoryId;
    private PlaceCategoryType categoryType;
    private Integer sequence;
    private boolean resetCandidates;
    private LocalDateTime updatedAt;
}
