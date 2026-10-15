package com.bready.server.place.controller;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.global.exception.GlobalExceptionHandler;
import com.bready.server.place.api.PlaceCategoryType;
import com.bready.server.place.api.PlaceSearchApi;
import com.bready.server.place.api.PlaceSearchResult;
import com.bready.server.place.exception.PlaceErrorCase;
import com.bready.server.place.external.RelayedErrorCase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlaceSearchController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class PlaceSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceSearchApi placeSearchApi;

    @Test
    @DisplayName("정상 검색 요청 → 200 반환")
    void search_success() throws Exception {
        given(placeSearchApi.searchPlaces(eq(PlaceCategoryType.CAFE), any(), eq(37.544), eq(127.055), eq(2000)))
                .willReturn(List.of(new PlaceSearchResult(
                        "kakao-1", "성수 카페", "서울 성동구", BigDecimal.valueOf(37.544), BigDecimal.valueOf(127.055), true)));

        mockMvc.perform(get("/api/v1/places/search")
                        .param("category", "CAFE")
                        .param("latitude", "37.544")
                        .param("longitude", "127.055"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].externalId").value("kakao-1"))
                .andExpect(jsonPath("$.data[0].name").value("성수 카페"))
                .andExpect(jsonPath("$.data[0].address").value("서울 성동구"))
                .andExpect(jsonPath("$.data[0].latitude").value(37.544))
                .andExpect(jsonPath("$.data[0].longitude").value(127.055))
                .andExpect(jsonPath("$.data[0].isIndoor").value(true));
    }

    @Test
    @DisplayName("실패 - Catalog가 카카오 오류를 돌려주면 같은 상태와 코드로 응답한다")
    void search_relayedKakaoError() throws Exception {
        willThrow(ApplicationException.from(new RelayedErrorCase(502, 4103, "카카오 장소 검색 요청이 잘못되었습니다.")))
                .given(placeSearchApi)
                .searchPlaces(any(), any(), any(), any(), any());

        mockMvc.perform(get("/api/v1/places/search").param("category", "CAFE"))
                .andDo(print())
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value(4103))
                .andExpect(jsonPath("$.message").value("카카오 장소 검색 요청이 잘못되었습니다."));
    }

    @Test
    @DisplayName("실패 - Catalog에 닿지 못하면 PLACE_CATALOG_UNAVAILABLE 503")
    void search_catalogUnavailable() throws Exception {
        willThrow(ApplicationException.from(PlaceErrorCase.PLACE_CATALOG_UNAVAILABLE))
                .given(placeSearchApi)
                .searchPlaces(any(), any(), any(), any(), any());

        mockMvc.perform(get("/api/v1/places/search").param("category", "CAFE"))
                .andDo(print())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value(PlaceErrorCase.PLACE_CATALOG_UNAVAILABLE.getErrorCode()));
    }

    @Test
    @DisplayName("위도만 전달 → LOCATION_REQUIRED")
    void search_location_invalid() throws Exception {
        mockMvc.perform(get("/api/v1/places/search").param("category", "CAFE").param("latitude", "37.544"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(PlaceErrorCase.LOCATION_REQUIRED.getErrorCode()));
    }

    @Test
    @DisplayName("결과 없음 → PLACE_NOT_FOUND")
    void search_empty_result() throws Exception {
        given(placeSearchApi.searchPlaces(any(), any(), any(), any(), any())).willReturn(List.of());

        mockMvc.perform(get("/api/v1/places/search")
                        .param("category", "CAFE")
                        .param("latitude", "37.544")
                        .param("longitude", "127.055"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(PlaceErrorCase.PLACE_NOT_FOUND.getErrorCode()));
    }

    @Test
    @DisplayName("category 누락 → 400")
    void search_category_missing() throws Exception {

        mockMvc.perform(get("/api/v1/places/search").param("latitude", "37.544").param("longitude", "127.055"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("radius 범위 초과 → 400")
    void search_radius_invalid() throws Exception {

        mockMvc.perform(get("/api/v1/places/search")
                        .param("category", "CAFE")
                        .param("latitude", "37.544")
                        .param("longitude", "127.055")
                        .param("radius", "20000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("위도 범위 초과 → 400")
    void search_latitude_invalid() throws Exception {

        mockMvc.perform(get("/api/v1/places/search")
                        .param("category", "CAFE")
                        .param("latitude", "200")
                        .param("longitude", "127"))
                .andExpect(status().isBadRequest());
    }
}
