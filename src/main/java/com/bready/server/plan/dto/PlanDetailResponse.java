package com.bready.server.plan.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlanDetailResponse {

    private PlanDto plan;

    @Builder.Default
    private List<PlanDetailCategoryDto> categories = List.of();
}
