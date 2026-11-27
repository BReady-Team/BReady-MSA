package com.bready.server.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bready.server.support.IntegrationTestSupport;
import com.bready.server.support.PlanningSteps;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 추천 서비스가 토큰만 전달해도 인증과 소유권 확인은 모놀리식이 한다는 것을 검증
@DisplayName("트리거 상황 조회 통합 테스트 - 다른 서비스가 사용자의 토큰으로 부른다")
class TriggerContextQueryIntegrationTest extends IntegrationTestSupport {

    private static final int PLAN_ACCESS_DENIED = 4002;

    private PlanningSteps steps;
    private String ownerToken;
    private long categoryId;
    private long triggerId;

    @BeforeEach
    void setUp() throws Exception {
        steps = new PlanningSteps(mockMvc);
        ownerToken = steps.signUpAndLogin("owner@example.com", "소유자");
        long planId = steps.createPlan(ownerToken);
        categoryId = steps.createCategory(ownerToken, planId, "CAFE");
        steps.createCandidate(ownerToken, planId, categoryId, "kakao-1");
        triggerId = steps.createTrigger(ownerToken, planId, categoryId);
    }

    @Test
    @DisplayName("성공 - 소유자의 토큰이면 트리거 상황을 돌려준다")
    void getTriggerContext_owner() throws Exception {
        getContext(ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.triggerId").value(triggerId))
                .andExpect(jsonPath("$.data.triggerType").value("WAITING_TOO_LONG"))
                .andExpect(jsonPath("$.data.planRegion").value("서울"))
                .andExpect(jsonPath("$.data.currentCategory.categoryId").value(categoryId))
                .andExpect(jsonPath("$.data.currentCategory.categoryType").value("CAFE"))
                .andExpect(jsonPath("$.data.planCategories.length()").value(1));
    }

    @Test
    @DisplayName("성공 - 소유자의 토큰이면 대표 후보의 장소 스냅샷을 돌려준다")
    void getRepresentativePlace_owner() throws Exception {
        getRepresentativePlace(ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.externalId").value("kakao-1"))
                .andExpect(jsonPath("$.data.address").value("서울 성동구"))
                .andExpect(jsonPath("$.data.latitude").value(37.5445))
                .andExpect(jsonPath("$.data.longitude").value(127.0557));
    }

    @Test
    @DisplayName("실패 - 토큰이 없으면 401")
    void getTriggerContext_noToken() throws Exception {
        getContext(null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("실패 - 남의 플랜의 트리거 상황을 조회하면 PLAN_ACCESS_DENIED")
    void getTriggerContext_notOwner() throws Exception {
        String otherToken = steps.signUpAndLogin("other@example.com", "다른사람");

        getContext(otherToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(PLAN_ACCESS_DENIED));
    }

    @Test
    @DisplayName("실패 - 남의 플랜의 대표 장소를 조회하면 PLAN_ACCESS_DENIED")
    void getRepresentativePlace_notOwner() throws Exception {
        String otherToken = steps.signUpAndLogin("other@example.com", "다른사람");

        getRepresentativePlace(otherToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(PLAN_ACCESS_DENIED));
    }

    private ResultActions getContext(String token) throws Exception {
        return perform(get("/internal/v1/triggers/{triggerId}/context", triggerId), token);
    }

    private ResultActions getRepresentativePlace(String token) throws Exception {
        return perform(get("/internal/v1/triggers/{triggerId}/representative-place", triggerId), token);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request).andDo(print());
    }
}
