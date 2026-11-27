package com.bready.recommendation.planning.api;

import com.bready.recommendation.catalog.api.PlaceCategoryType;

// 경계 밖으로 나가는 플랜 카테고리 정보
public record CategoryInfo(Long categoryId, PlaceCategoryType categoryType, Integer sequence) {}
