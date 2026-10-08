package com.bready.server.plan.dto;

import java.util.List;

import com.bready.server.place.domain.PlaceCategoryType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlanDetailCategoryDto {

    private Long planCategoryId;
    private PlaceCategoryType categoryType;
    private Integer sequence;

    private Long representativeCandidateId;

    @Builder.Default
    private List<PlanDetailCandidateDto> candidates = List.of();
}
