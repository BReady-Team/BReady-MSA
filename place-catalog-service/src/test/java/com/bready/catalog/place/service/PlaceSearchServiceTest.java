package com.bready.catalog.place.service;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bready.catalog.place.domain.PlaceCategoryType;
import com.bready.catalog.place.dto.PlaceSearchRequest;
import com.bready.catalog.place.dto.PlaceSearchResponse;
import com.bready.catalog.place.external.KakaoKeywordSearchCondition;
import com.bready.catalog.place.external.KakaoKeywordSearchResponse;
import com.bready.catalog.place.external.KakaoPlaceClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PlaceSearchServiceTest {

    @Mock
    KakaoPlaceClient kakaoPlaceClient;

    @InjectMocks
    PlaceSearchService placeSearchService;

    @Test
    @DisplayName("성공 - 카테고리로 검색어와 카카오 카테고리 코드를 정해 카카오에 묻는다")
    void search_buildsCondition() {
        given(kakaoPlaceClient.searchByKeyword(any())).willReturn(List.of());
        ArgumentCaptor<KakaoKeywordSearchCondition> captor = ArgumentCaptor.forClass(KakaoKeywordSearchCondition.class);

        placeSearchService.search(new PlaceSearchRequest(PlaceCategoryType.CAFE, "성수", 37.544, 127.055, 1000));

        then(kakaoPlaceClient).should().searchByKeyword(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new KakaoKeywordSearchCondition("성수 카페", 37.544, 127.055, 1000, "CE7"));
    }

    @Test
    @DisplayName("성공 - 카카오 장소를 응답으로 바꾸고 실내 여부는 카테고리에서 가져온다")
    void search_mapsDocuments() {
        given(kakaoPlaceClient.searchByKeyword(any()))
                .willReturn(List.of(
                        new KakaoKeywordSearchResponse.Document("123", "서울숲", "서울 성동구 지번", "", "127.037", "37.544")));

        List<PlaceSearchResponse> responses =
                placeSearchService.search(new PlaceSearchRequest(PlaceCategoryType.WALK, null, null, null, null));

        assertThat(responses)
                .containsExactly(new PlaceSearchResponse(
                        "kakao-123", "서울숲", "서울 성동구 지번", new BigDecimal("37.544"), new BigDecimal("127.037"), false));
    }

    @Test
    @DisplayName("성공 - 결과가 없으면 예외가 아니라 빈 목록이다")
    void search_empty() {
        given(kakaoPlaceClient.searchByKeyword(any())).willReturn(List.of());

        List<PlaceSearchResponse> responses =
                placeSearchService.search(new PlaceSearchRequest(PlaceCategoryType.CAFE, null, null, null, null));

        assertThat(responses).isEmpty();
    }
}
