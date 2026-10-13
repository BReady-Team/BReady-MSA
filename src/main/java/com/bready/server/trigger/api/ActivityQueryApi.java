package com.bready.server.trigger.api;

import java.time.LocalDateTime;

// Planning이 다른 컨텍스트에 여는 조회 창구 (트리거·결정·전환 기록)
// startAt이 null이면 전체 기간이다
public interface ActivityQueryApi {

    long countSwitches(Long ownerId, LocalDateTime startAt);

    long countDecisions(Long ownerId, LocalDateTime startAt);
}
