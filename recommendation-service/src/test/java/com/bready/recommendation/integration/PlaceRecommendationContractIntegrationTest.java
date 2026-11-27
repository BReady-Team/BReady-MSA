package com.bready.recommendation.integration;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.ResourceAccessException;

import com.bready.recommendation.catalog.api.PlaceCategoryType;
import com.bready.recommendation.catalog.api.PlaceSearchResult;
import com.bready.recommendation.global.client.RemoteResponse;
import com.bready.recommendation.planning.api.CategoryInfo;
import com.bready.recommendation.planning.api.RepresentativePlace;
import com.bready.recommendation.planning.api.TriggerContext;
import com.bready.recommendation.planning.api.TriggerType;
import com.bready.recommendation.support.IntegrationTestSupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 추천을 떼어내도 장소 추천 응답은 그대로인지 검증
@DisplayName("장소 추천 응답 계약 통합 테스트 - 카페에서 대기 과다 트리거")
class PlaceRecommendationContractIntegrationTest extends IntegrationTestSupport {

    private static final String AUTHORIZATION = "Bearer owner-token";
    private static final Long TRIGGER_ID = 10L;
    private static final CategoryInfo CAFE = new CategoryInfo(4L, PlaceCategoryType.CAFE, 1);

    private static final RemoteResponse<List<PlaceSearchResult>> CATALOG_TWO_PLACES = new RemoteResponse<>(
            null,
            "success",
            List.of(
                    new PlaceSearchResult(
                            "kakao-1",
                            "지금 대표 카페",
                            "서울 성동구",
                            new BigDecimal("37.5445"),
                            new BigDecimal("127.0557"),
                            true),
                    new PlaceSearchResult(
                            "kakao-2",
                            "새 카페",
                            "서울 성동구 성수이로 1",
                            new BigDecimal("37.5450"),
                            new BigDecimal("127.0560"),
                            true)));

    private static final RemoteResponse<List<PlaceSearchResult>> CATALOG_NO_PLACES =
            new RemoteResponse<>(null, "success", List.of());

    @BeforeEach
    void givenOwnersTrigger() {
        given(planningHttpClient.getTriggerContext(AUTHORIZATION, TRIGGER_ID))
                .willReturn(new RemoteResponse<>(
                        null,
                        "success",
                        new TriggerContext(TRIGGER_ID, TriggerType.WAITING_TOO_LONG, "서울", CAFE, List.of(CAFE))));
        given(planningHttpClient.getRepresentativePlace(AUTHORIZATION, TRIGGER_ID))
                .willReturn(new RemoteResponse<>(
                        null,
                        "success",
                        new RepresentativePlace(
                                "kakao-1", "서울 성동구", new BigDecimal("37.5445"), new BigDecimal("127.0557"))));
    }

    @Test
    @DisplayName("성공 - 대표 후보 주변을 검색하고, 지금 대표인 장소는 빼고 추천한다")
    void recommendPlaces_success() throws Exception {
        given(placeCatalogHttpClient.searchPlaces(any(), any(), any(), any(), any()))
                .willReturn(CATALOG_TWO_PLACES);

        recommend(AUTHORIZATION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].externalId").value("kakao-2"))
                .andExpect(jsonPath("$.data.items[0].name").value("새 카페"))
                .andExpect(jsonPath("$.data.items[0].address").value("서울 성동구 성수이로 1"))
                .andExpect(jsonPath("$.data.items[0].latitude").value(37.5450))
                .andExpect(jsonPath("$.data.items[0].longitude").value(127.0560))
                .andExpect(jsonPath("$.data.items[0].isIndoor").value(true))
                .andExpect(jsonPath("$.data.items[0].distanceMeters").isNumber())
                .andExpect(jsonPath("$.data.items[0].reason").value("혼잡을 피하기 위해 주변 대체 후보를 추천합니다."));
    }

    @Test
    @DisplayName("성공 - 기준 위치는 대표 장소 스냅샷에서 얻고, Catalog에는 주변 검색만 요청한다")
    void recommendPlaces_searchesAroundRepresentativePlace() throws Exception {
        given(placeCatalogHttpClient.searchPlaces(any(), any(), any(), any(), any()))
                .willReturn(CATALOG_TWO_PLACES);

        recommend(AUTHORIZATION).andExpect(status().isOk());

        then(placeCatalogHttpClient)
                .should()
                .searchPlaces(eq(PlaceCategoryType.CAFE), any(), eq(37.5445), eq(127.0557), any());
        then(placeCatalogHttpClient).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("성공 - 주변에 검색되는 장소가 없으면 에러가 아니라 빈 목록을 돌려준다")
    void recommendPlaces_noSearchResult() throws Exception {
        given(placeCatalogHttpClient.searchPlaces(any(), any(), any(), any(), any()))
                .willReturn(CATALOG_NO_PLACES);

        recommend(AUTHORIZATION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("실패 - 남의 플랜의 트리거면 모놀리식의 PLAN_ACCESS_DENIED를 그대로 전달하고 Catalog는 부르지 않는다")
    void recommendPlaces_notOwner() throws Exception {
        String otherAuthorization = "Bearer other-token";
        given(planningHttpClient.getTriggerContext(otherAuthorization, TRIGGER_ID))
                .willThrow(
                        errorResponse(
                                HttpStatus.FORBIDDEN,
                                """
                        {"errorCode":4002,"message":"플랜에 대한 접근 권한이 없습니다."}
                        """));

        recommend(otherAuthorization)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(4002))
                .andExpect(jsonPath("$.message").value("플랜에 대한 접근 권한이 없습니다."));

        then(placeCatalogHttpClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("실패 - 토큰이 없으면 헤더 없이 모놀리식을 부르고 그 401을 전달한다")
    void recommendPlaces_noToken() throws Exception {
        given(planningHttpClient.getTriggerContext(null, TRIGGER_ID)).willThrow(unauthorized());

        recommend(null).andExpect(status().isUnauthorized());

        then(placeCatalogHttpClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("실패 - 모놀리식에 닿지 못하면 PLANNING_UNAVAILABLE")
    void recommendPlaces_planningUnavailable() throws Exception {
        given(planningHttpClient.getTriggerContext(AUTHORIZATION, TRIGGER_ID))
                .willThrow(new ResourceAccessException("Connection refused"));

        recommend(AUTHORIZATION)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value(5033));
    }

    private ResultActions recommend(String authorization) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/recommendations/places")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"triggerId": %d}
                        """
                        .formatted(TRIGGER_ID));
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request).andDo(print());
    }
}
