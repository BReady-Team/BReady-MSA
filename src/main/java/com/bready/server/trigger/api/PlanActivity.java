package com.bready.server.trigger.api;

import java.time.LocalDateTime;

// 한 플랜 안의 활동 한 건, id는 전환이면 전환 기록 ID, 유지면 결정 ID
public record PlanActivity(Long id, TriggerType triggerType, LocalDateTime createdAt) {}
