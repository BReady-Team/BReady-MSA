package com.bready.server.trigger.api;

import java.time.LocalDateTime;

// 여러 플랜에 걸친 활동 한 건. id는 전환이면 전환 기록 ID, 유지면 결정 ID
public record RecentActivity(
        Long id, Long planId, String planTitle, TriggerType triggerType, LocalDateTime createdAt) {}
