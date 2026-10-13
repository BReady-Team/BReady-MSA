package com.bready.server.plan.api;

import java.util.List;
import java.util.Optional;

// Planning이 다른 컨텍스트에 여는 조회 창구 (플랜)
public interface PlanQueryApi {

    // 삭제되지 않은 플랜 수
    long countActivePlans(Long ownerId);

    // 그 사용자의 플랜이 아니면 빈 Optional
    Optional<PlanSummary> findOwnedPlan(Long planId, Long ownerId);

    // 삭제되지 않은 플랜을 플랜 날짜가 늦은 것부터
    List<PlanSummary> findRecentPlans(Long ownerId, int limit);
}
