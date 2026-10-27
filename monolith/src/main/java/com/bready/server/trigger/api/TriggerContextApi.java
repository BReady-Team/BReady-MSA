package com.bready.server.trigger.api;

// Planning이 다른 컨텍스트에 여는 조회 창구 (트리거가 난 상황)
public interface TriggerContextApi {

    // 트리거가 없으면 TRIGGER_NOT_FOUND, 요청한 사용자의 플랜이 아니면 PLAN_ACCESS_DENIED
    TriggerContext getTriggerContext(Long userId, Long triggerId);

    // 그 카테고리의 대표 후보가 가리키는 장소
    // 대표 후보가 아직 없으면 CATEGORY_STATE_NOT_FOUND, 대표 후보가 삭제됐으면 PLACE_CANDIDATE_NOT_FOUND
    RepresentativePlace getRepresentativePlace(Long categoryId);
}
