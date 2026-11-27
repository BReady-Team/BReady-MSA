package com.bready.recommendation.integration;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.bready.recommendation.catalog.api.PlaceCategoryType;
import com.bready.recommendation.catalog.api.PlaceSearchResult;
import com.bready.recommendation.global.client.RemoteResponse;
import com.bready.recommendation.planning.api.CategoryInfo;
import com.bready.recommendation.planning.api.RepresentativePlace;
import com.bready.recommendation.planning.api.TriggerContext;
import com.bready.recommendation.planning.api.TriggerType;
import com.bready.recommendation.support.IntegrationTestSupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 같은 카테고리/같은 위치라도 결과가 달라야 하는 요청끼리 캐시를 나눠 쓰지 않는지 검증
@DisplayName("장소 추천 캐시 통합 테스트 - 같은 위치의 카페 두 곳을 각자 대표로 둔 두 사용자")
class PlaceRecommendationCacheIntegrationTest extends IntegrationTestSupport {

    private static final String FIRST_USER = "Bearer first-token";
    private static final String SECOND_USER = "Bearer second-token";
    private static final Long FIRST_TRIGGER_ID = 10L;
    private static final Long SECOND_TRIGGER_ID = 11L;
    private static final BigDecimal LATITUDE = new BigDecimal("37.5445");
    private static final BigDecimal LONGITUDE = new BigDecimal("127.0557");
    private static final CategoryInfo CAFE = new CategoryInfo(4L, PlaceCategoryType.CAFE, 1);

    @BeforeEach
    void givenTwoUsersAtSamePosition() {
        givenTrigger(FIRST_USER, FIRST_TRIGGER_ID, TriggerType.WAITING_TOO_LONG, "kakao-1");
        givenTrigger(SECOND_USER, SECOND_TRIGGER_ID, TriggerType.WAITING_TOO_LONG, "kakao-2");
        given(placeCatalogHttpClient.searchPlaces(any(), any(), any(), any(), any()))
                .willReturn(new RemoteResponse<>(
                        null, "success", List.of(place("kakao-1"), place("kakao-2"), place("kakao-3"))));
    }

    @Test
    @DisplayName("성공 - 다른 사용자의 추천이 캐시에 있어도 내 대표 장소는 내 추천에서 빠진다")
    void recommendPlaces_excludesOwnRepresentativeAfterOthersRequest() throws Exception {
        recommend(FIRST_USER, FIRST_TRIGGER_ID, "")
                .andExpect(jsonPath("$.data.items[0].externalId").value("kakao-2"));

        recommend(SECOND_USER, SECOND_TRIGGER_ID, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].externalId").value("kakao-1"))
                .andExpect(jsonPath("$.data.items[1].externalId").value("kakao-3"));
    }

    @Test
    @DisplayName("성공 - 앞선 요청이 더 많이 받아 갔어도 size만큼만 돌려준다")
    void recommendPlaces_appliesSizeAfterLargerRequest() throws Exception {
        recommend(FIRST_USER, FIRST_TRIGGER_ID, "")
                .andExpect(jsonPath("$.data.items.length()").value(2));

        recommend(FIRST_USER, FIRST_TRIGGER_ID, "?size=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    @DisplayName("성공 - 트리거 종류가 다르면 그 트리거의 사유로 추천한다")
    void recommendPlaces_usesReasonOfOwnTriggerType() throws Exception {
        givenTrigger(SECOND_USER, SECOND_TRIGGER_ID, TriggerType.PLACE_CLOSED, "kakao-1");
        recommend(FIRST_USER, FIRST_TRIGGER_ID, "")
                .andExpect(jsonPath("$.data.items[0].reason").value("혼잡을 피하기 위해 주변 대체 후보를 추천합니다."));

        recommend(SECOND_USER, SECOND_TRIGGER_ID, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].reason").value("영업 종료 상황을 고려해 주변 대체 장소를 추천합니다."));
    }

    @Test
    @DisplayName("성공 - 조건이 모두 같은 요청은 캐시를 써서 Catalog를 한 번만 부른다")
    void recommendPlaces_reusesCacheForSameCondition() throws Exception {
        recommend(FIRST_USER, FIRST_TRIGGER_ID, "").andExpect(status().isOk());
        recommend(FIRST_USER, FIRST_TRIGGER_ID, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2));

        then(placeCatalogHttpClient).should(times(1)).searchPlaces(any(), any(), any(), any(), any());
    }

    private void givenTrigger(
            String authorization, Long triggerId, TriggerType triggerType, String representativeExternalId) {
        given(planningHttpClient.getTriggerContext(authorization, triggerId))
                .willReturn(new RemoteResponse<>(
                        null, "success", new TriggerContext(triggerId, triggerType, "서울", CAFE, List.of(CAFE))));
        given(planningHttpClient.getRepresentativePlace(authorization, triggerId))
                .willReturn(new RemoteResponse<>(
                        null,
                        "success",
                        new RepresentativePlace(representativeExternalId, "서울 성동구", LATITUDE, LONGITUDE)));
    }

    private PlaceSearchResult place(String externalId) {
        return new PlaceSearchResult(externalId, externalId, "서울 성동구", LATITUDE, LONGITUDE, true);
    }

    private ResultActions recommend(String authorization, Long triggerId, String queryString) throws Exception {
        return mockMvc.perform(post("/api/v1/recommendations/places" + queryString)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"triggerId": %d}
                                """
                                        .formatted(triggerId)))
                .andDo(print());
    }
}
