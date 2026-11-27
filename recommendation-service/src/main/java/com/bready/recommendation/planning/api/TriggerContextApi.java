package com.bready.recommendation.planning.api;

public interface TriggerContextApi {

    // 토큰이 없거나 틀리면 401, 트리거가 없으면 TRIGGER_NOT_FOUND, 남의 플랜이면 PLAN_ACCESS_DENIED
    TriggerContext getTriggerContext(String authorization, Long triggerId);

    // 트리거가 난 카테고리의 대표 후보가 가리키는 장소
    RepresentativePlace getRepresentativePlace(String authorization, Long triggerId);
}
