package com.bready.recommendation.integration;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bready.recommendation.catalog.api.PlaceCategoryType;
import com.bready.recommendation.global.client.RemoteResponse;
import com.bready.recommendation.planning.api.CategoryInfo;
import com.bready.recommendation.planning.api.TriggerContext;
import com.bready.recommendation.planning.api.TriggerType;
import com.bready.recommendation.support.IntegrationTestSupport;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 추천을 떼어내도 카테고리 추천 응답은 그대로인지 검증
@DisplayName("카테고리 추천 응답 계약 통합 테스트 - 식사·카페·산책 플랜의 카페에서 대기 과다 트리거")
class CategoryRecommendationContractIntegrationTest extends IntegrationTestSupport {

    private static final String AUTHORIZATION = "Bearer owner-token";
    private static final Long TRIGGER_ID = 10L;

    @Test
    @DisplayName("성공 - 현재(카페)와 앞뒤(식사·산책) 카테고리를 뺀 나머지를 트리거 사유와 함께 추천한다")
    void recommendCategories_success() throws Exception {
        CategoryInfo meal = new CategoryInfo(3L, PlaceCategoryType.MEAL, 1);
        CategoryInfo cafe = new CategoryInfo(4L, PlaceCategoryType.CAFE, 2);
        CategoryInfo walk = new CategoryInfo(5L, PlaceCategoryType.WALK, 3);
        given(planningHttpClient.getTriggerContext(AUTHORIZATION, TRIGGER_ID))
                .willReturn(new RemoteResponse<>(
                        null,
                        "success",
                        new TriggerContext(
                                TRIGGER_ID, TriggerType.WAITING_TOO_LONG, "서울", cafe, List.of(meal, cafe, walk))));

        recommend(AUTHORIZATION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].categoryType").value("EXHIBITION"))
                .andExpect(jsonPath("$.data.items[0].label").value("전시"))
                .andExpect(jsonPath("$.data.items[0].reason").value("혼잡을 피할 수 있는 대체 활동입니다."))
                .andExpect(jsonPath("$.data.items[1].categoryType").value("SHOPPING"))
                .andExpect(jsonPath("$.data.items[2].categoryType").value("REST"));

        then(planningHttpClient).should().getTriggerContext(AUTHORIZATION, TRIGGER_ID);
        then(placeCatalogHttpClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("실패 - 남의 플랜의 트리거면 모놀리식의 PLAN_ACCESS_DENIED를 그대로 전달한다")
    void recommendCategories_notOwner() throws Exception {
        given(planningHttpClient.getTriggerContext(AUTHORIZATION, TRIGGER_ID))
                .willThrow(
                        errorResponse(
                                HttpStatus.FORBIDDEN,
                                """
                        {"errorCode":4002,"message":"플랜에 대한 접근 권한이 없습니다."}
                        """));

        recommend(AUTHORIZATION)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(4002));
    }

    @Test
    @DisplayName("실패 - 없는 트리거면 모놀리식의 TRIGGER_NOT_FOUND를 그대로 전달한다")
    void recommendCategories_triggerNotFound() throws Exception {
        given(planningHttpClient.getTriggerContext(AUTHORIZATION, TRIGGER_ID))
                .willThrow(
                        errorResponse(
                                HttpStatus.NOT_FOUND,
                                """
                        {"errorCode":7001,"message":"트리거를 찾을 수 없습니다."}
                        """));

        recommend(AUTHORIZATION)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(7001));
    }

    @Test
    @DisplayName("실패 - 토큰이 없으면 헤더 없이 모놀리식을 부르고 그 401을 전달한다")
    void recommendCategories_noToken() throws Exception {
        given(planningHttpClient.getTriggerContext(null, TRIGGER_ID)).willThrow(unauthorized());

        recommend(null).andExpect(status().isUnauthorized());
    }

    private ResultActions recommend(String authorization) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/recommendations/categories")
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
