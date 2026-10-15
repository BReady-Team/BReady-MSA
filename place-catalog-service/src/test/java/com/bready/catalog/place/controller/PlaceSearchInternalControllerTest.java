package com.bready.catalog.place.controller;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bready.catalog.global.exception.ApplicationException;
import com.bready.catalog.place.domain.PlaceCategoryType;
import com.bready.catalog.place.dto.PlaceSearchRequest;
import com.bready.catalog.place.dto.PlaceSearchResponse;
import com.bready.catalog.place.exception.PlaceErrorCase;
import com.bready.catalog.place.service.PlaceSearchService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlaceSearchInternalController.class)
class PlaceSearchInternalControllerTest {

    private static final String SEARCH_URL = "/internal/v1/places/search";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PlaceSearchService placeSearchService;

    @Test
    @DisplayName("성공 - 검색 결과를 CommonResponse로 감싸 돌려준다")
    void searchPlaces_success() throws Exception {
        given(placeSearchService.search(any()))
                .willReturn(List.of(new PlaceSearchResponse(
                        "kakao-123", "성수 카페", "서울 성동구", new BigDecimal("37.544"), new BigDecimal("127.055"), true)));

        mockMvc.perform(get(SEARCH_URL)
                        .param("category", "CAFE")
                        .param("latitude", "37.544")
                        .param("longitude", "127.055"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andExpect(jsonPath("$.data[0].externalId").value("kakao-123"))
                .andExpect(jsonPath("$.data[0].isIndoor").value(true));
    }

    @Test
    @DisplayName("성공 - radius가 없으면 기본값 2000으로 검색한다")
    void searchPlaces_defaultRadius() throws Exception {
        given(placeSearchService.search(any())).willReturn(List.of());

        mockMvc.perform(get(SEARCH_URL).param("category", "CAFE")).andDo(print());

        then(placeSearchService)
                .should()
                .search(new PlaceSearchRequest(
                        PlaceCategoryType.CAFE, null, null, null, PlaceSearchRequest.DEFAULT_RADIUS));
    }

    @Test
    @DisplayName("성공 - 결과가 없으면 404가 아니라 200과 빈 목록이다")
    void searchPlaces_empty() throws Exception {
        given(placeSearchService.search(any())).willReturn(List.of());

        mockMvc.perform(get(SEARCH_URL).param("category", "CAFE"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("실패 - category가 없으면 400")
    void searchPlaces_missingCategory() throws Exception {
        mockMvc.perform(get(SEARCH_URL))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(4001))
                .andExpect(jsonPath("$.message").value("카테고리를 선택해주세요."));
    }

    @Test
    @DisplayName("실패 - 없는 category면 400이고 내부 변환 메시지를 내보내지 않는다")
    void searchPlaces_unknownCategory() throws Exception {
        mockMvc.perform(get(SEARCH_URL).param("category", "FOO"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(4001))
                .andExpect(jsonPath("$.message").value("요청 값이 유효하지 않습니다."));
    }

    @Test
    @DisplayName("실패 - radius가 10000을 넘으면 400")
    void searchPlaces_radiusTooLarge() throws Exception {
        mockMvc.perform(get(SEARCH_URL).param("category", "CAFE").param("radius", "20000"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(4001));
    }

    @Test
    @DisplayName("실패 - 카카오 오류는 KAKAO_CLIENT_ERROR 502로 전달한다")
    void searchPlaces_kakaoError() throws Exception {
        willThrow(ApplicationException.from(PlaceErrorCase.KAKAO_CLIENT_ERROR))
                .given(placeSearchService)
                .search(any());

        mockMvc.perform(get(SEARCH_URL).param("category", "CAFE"))
                .andDo(print())
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value(4103));
    }
}
