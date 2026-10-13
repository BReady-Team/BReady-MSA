package com.bready.server.trigger.api;

// Planning이 다른 컨텍스트에 여는 조회 창구 (트리거가 난 상황)
public interface TriggerContextApi {

    // 트리거가 없으면 TRIGGER_NOT_FOUND, 요청한 사용자의 플랜이 아니면 PLAN_ACCESS_DENIED
    TriggerContext getTriggerContext(Long userId, Long triggerId);
}
