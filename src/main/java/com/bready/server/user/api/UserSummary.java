package com.bready.server.user.api;

// 다른 컨텍스트가 화면에 보여 줄 때 쓰는 사용자 정보
public record UserSummary(Long userId, String nickname, String profileImageUrl) {}
