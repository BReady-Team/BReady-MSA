package com.bready.server.plan.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlanListResponse {

    private List<PlanListItemDto> items;
    private PageInfo pageInfo;
}
