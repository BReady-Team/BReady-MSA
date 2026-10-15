package com.bready.catalog.place.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.bready.catalog.place.domain.PlaceCategoryType;
import com.bready.catalog.place.dto.PlaceSearchRequest;
import com.bready.catalog.place.dto.PlaceSearchResponse;
import com.bready.catalog.place.external.KakaoKeywordSearchCondition;
import com.bready.catalog.place.external.KakaoPlaceClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private final KakaoPlaceClient kakaoPlaceClient;

    // 결과가 없으면 빈 목록
    public List<PlaceSearchResponse> search(PlaceSearchRequest request) {
        PlaceCategoryType category = request.category();
        String query = category.searchQuery(request.keyword());
        log.debug("카카오 장소 검색 query={}", query);

        KakaoKeywordSearchCondition condition = new KakaoKeywordSearchCondition(
                query, request.latitude(), request.longitude(), request.radius(), category.getKakaoCategoryCode());

        return kakaoPlaceClient.searchByKeyword(condition).stream()
                .map(document -> PlaceSearchResponse.of(document, category.isIndoor()))
                .toList();
    }
}
