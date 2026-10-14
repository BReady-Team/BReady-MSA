package com.bready.server.plan.dto;

import jakarta.validation.constraints.NotNull;

import com.bready.server.place.api.PlaceCategoryType;

import lombok.Getter;

@Getter
public class PlanCategoryTypeUpdateRequest {
    @NotNull
    private PlaceCategoryType categoryType;
}
