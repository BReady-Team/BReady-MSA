package com.bready.server.plan.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlanUpdateResponse {

    private Long planId;
    private LocalDateTime updatedAt;
}
