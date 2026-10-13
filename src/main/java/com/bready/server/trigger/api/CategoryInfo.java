package com.bready.server.trigger.api;

import com.bready.server.place.api.PlaceCategoryType;

// 경계 밖으로 나가는 플랜 카테고리 정보
public record CategoryInfo(Long categoryId, PlaceCategoryType categoryType, Integer sequence) {}
