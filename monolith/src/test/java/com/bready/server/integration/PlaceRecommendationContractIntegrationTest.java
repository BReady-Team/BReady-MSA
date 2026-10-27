package com.bready.server.integration;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.bready.server.place.api.PlaceCategoryType;
import com.bready.server.place.api.PlaceSearchResult;
import com.bready.server.place.external.PlaceCatalogResponse;
import com.bready.server.support.IntegrationTestSupport;
import com.bready.server.support.PlanningSteps;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.clearInvocations;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Recommendation이 Planning과 Place Catalog의 데이터를 얻는 방법을 바꿔도 장소 추천 응답은 그대로인지 검증
@DisplayName("장소 추천 응답 계약 통합 테스트 - 카페에서 대기 과다 트리거")
class PlaceRecommendationContractIntegrationTest extends IntegrationTestSupport {

    private static final int PLAN_ACCESS_DENIED = 4002;

    // 카카오 응답을 장소로 바꾸는 규칙(kakao- 접두어, 도로명 주소 우선)은 place-catalog-service가 검증
    private static final PlaceCatalogResponse<List<PlaceSearchResult>> CATALOG_TWO_PLACES = new PlaceCatalogResponse<>(
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

    private static final PlaceCatalogResponse<List<PlaceSearchResult>> CATALOG_NO_PLACES =
            new PlaceCatalogResponse<>(null, "success", List.of());

    private PlanningSteps steps;
    private String token;
    private long triggerId;

    @BeforeEach
    void setUp() throws Exception {
        steps = new PlanningSteps(mockMvc);
        token = steps.signUpAndLogin("owner@example.com", "소유자");
        long planId = steps.createPlan(token);
        long categoryId = steps.createCategory(token, planId, "CAFE");
        steps.createCandidate(token, planId, categoryId, "kakao-1");
        triggerId = steps.createTrigger(token, planId, categoryId);
    }

    @Test
    @DisplayName("성공 - 대표 후보 주변을 검색하고, 지금 대표인 장소는 빼고 추천한다")
    void recommendPlaces_success() throws Exception {
        given(placeCatalogHttpClient.searchPlaces(any(), any(), any(), any(), any()))
                .willReturn(CATALOG_TWO_PLACES);

        recommend(token)
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
    @DisplayName("성공 - 기준 위치는 대표 후보의 스냅샷에서 얻고, Catalog에는 주변 검색만 요청한다")
    void recommendPlaces_readsSnapshotWithoutCatalog() throws Exception {
        clearInvocations(placeCatalogHttpClient);
        given(placeCatalogHttpClient.searchPlaces(any(), any(), any(), any(), any()))
                .willReturn(CATALOG_TWO_PLACES);

        recommend(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].externalId").value("kakao-2"));

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

        recommend(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("실패 - 남의 플랜의 트리거로 추천을 요청하면 PLAN_ACCESS_DENIED")
    void recommendPlaces_notOwner() throws Exception {
        String otherToken = steps.signUpAndLogin("other@example.com", "다른사람");

        recommend(otherToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(PLAN_ACCESS_DENIED));
    }

    private ResultActions recommend(String userToken) throws Exception {
        return mockMvc.perform(post("/api/v1/recommendations/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"triggerId": %d}
                                """
                                        .formatted(triggerId)))
                .andDo(print());
    }
}
