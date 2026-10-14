package com.bready.server.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.bready.server.support.IntegrationTestSupport;
import com.bready.server.support.PlanningSteps;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Recommendation이 Planning의 데이터를 얻는 방법을 바꿔도 카테고리 추천 응답은 그대로인지 검증
// 식사 → 카페 → 산책 플랜의 카페에서 트리거가 난 상황을 가정
@DisplayName("카테고리 추천 응답 계약 통합 테스트 - 식사·카페·산책 플랜의 카페에서 트리거")
class CategoryRecommendationContractIntegrationTest extends IntegrationTestSupport {

    private static final int PLAN_ACCESS_DENIED = 4002;
    private static final int TRIGGER_NOT_FOUND = 7001;

    private PlanningSteps steps;
    private String token;
    private long triggerId;

    @BeforeEach
    void setUp() throws Exception {
        steps = new PlanningSteps(mockMvc);
        token = steps.signUpAndLogin("owner@example.com", "소유자");
        long planId = steps.createPlan(token);
        steps.createCategory(token, planId, "MEAL");
        long cafeCategoryId = steps.createCategory(token, planId, "CAFE");
        steps.createCategory(token, planId, "WALK");
        steps.createCandidate(token, planId, cafeCategoryId, "kakao-1");
        triggerId = steps.createTrigger(token, planId, cafeCategoryId);
    }

    @Test
    @DisplayName("성공 - 현재(카페)와 앞뒤(식사·산책) 카테고리를 뺀 나머지를 트리거 사유와 함께 추천한다")
    void recommendCategories_success() throws Exception {
        recommend(token, triggerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].categoryType").value("EXHIBITION"))
                .andExpect(jsonPath("$.data.items[0].label").value("전시"))
                .andExpect(jsonPath("$.data.items[0].reason").value("혼잡을 피할 수 있는 대체 활동입니다."))
                .andExpect(jsonPath("$.data.items[1].categoryType").value("SHOPPING"))
                .andExpect(jsonPath("$.data.items[2].categoryType").value("REST"));
    }

    @Test
    @DisplayName("실패 - 남의 플랜의 트리거로 추천을 요청하면 PLAN_ACCESS_DENIED")
    void recommendCategories_notOwner() throws Exception {
        String otherToken = steps.signUpAndLogin("other@example.com", "다른사람");

        recommend(otherToken, triggerId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(PLAN_ACCESS_DENIED));
    }

    @Test
    @DisplayName("실패 - 없는 트리거로 추천을 요청하면 TRIGGER_NOT_FOUND")
    void recommendCategories_triggerNotFound() throws Exception {
        recommend(token, 9999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(TRIGGER_NOT_FOUND));
    }

    private ResultActions recommend(String userToken, long targetTriggerId) throws Exception {
        return mockMvc.perform(post("/api/v1/recommendations/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"triggerId": %d}
                                """
                                        .formatted(targetTriggerId)))
                .andDo(print());
    }
}
