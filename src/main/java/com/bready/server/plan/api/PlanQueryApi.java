package com.bready.server.plan.api;

// Planning이 다른 컨텍스트에 여는 조회 창구 (플랜)
public interface PlanQueryApi {

    // 삭제되지 않은 플랜 수
    long countActivePlans(Long ownerId);
}
