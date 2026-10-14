package com.bready.server.plan.api;

import java.time.LocalDate;

// 경계 밖으로 나가는 플랜 정보
public record PlanSummary(Long planId, String title, LocalDate planDate, String region) {}
