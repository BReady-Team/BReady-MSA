package com.bready.server.integration;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bready.server.support.IntegrationTestSupport;
import com.bready.server.support.PlanningSteps;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Stats가 Planning의 데이터를 얻는 방법을 바꿔도 통계 응답은 그대로여야 하는것을 검증
@DisplayName("통계 응답 계약 통합 테스트 - 유지 1건과 전환 1건 이후")
class StatsContractIntegrationTest extends IntegrationTestSupport {

    private String token;
    private long planId;
    private long keepDecisionId;

    @BeforeEach
    void setUp() throws Exception {
        PlanningSteps steps = new PlanningSteps(mockMvc);
        token = steps.signUpAndLogin("owner@example.com", "소유자");
        planId = steps.createPlan(token);
        long categoryId = steps.createCategory(token, planId);
        steps.createCandidate(token, planId, categoryId, "kakao-1");
        long secondCandidateId = steps.createCandidate(token, planId, categoryId, "kakao-2");

        long keepTriggerId = steps.createTrigger(token, planId, categoryId);
        keepDecisionId = steps.decideKeep(token, keepTriggerId);

        long switchTriggerId = steps.createTrigger(token, planId, categoryId);
        long switchDecisionId = steps.decideSwitch(token, switchTriggerId);
        steps.switchTo(token, switchDecisionId, secondCandidateId);
    }

    @Test
    @DisplayName("성공 - 요약은 플랜 1개, 전환 1회, 결정 2건, 플랜당 평균 전환 1.0")
    void getSummary() throws Exception {
        perform(get("/api/v1/stats/summary").param("period", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("ALL"))
                .andExpect(jsonPath("$.data.totalPlans").value(1))
                .andExpect(jsonPath("$.data.totalSwitches").value(1))
                .andExpect(jsonPath("$.data.avgSwitchesPerPlan").value(1.0))
                .andExpect(jsonPath("$.data.recentCount").value(2));
    }

    @Test
    @DisplayName("성공 - 트리거 분석은 전체 2건이고 유형 다섯 개를 모두 돌려준다")
    void getTriggerStats() throws Exception {
        perform(get("/api/v1/stats/triggers").param("period", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("ALL"))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(5))
                .andExpect(jsonPath("$.data.items[?(@.triggerType == 'WAITING_TOO_LONG')].count")
                        .value(2))
                .andExpect(jsonPath("$.data.items[?(@.triggerType == 'WAITING_TOO_LONG')].percentage")
                        .value(100))
                .andExpect(jsonPath("$.data.items[?(@.triggerType == 'WEATHER_BAD')].count")
                        .value(0));
    }

    @Test
    @DisplayName("성공 - 최근 활동은 전환과 유지를 최신순으로 합쳐 돌려준다")
    void getRecentActivities() throws Exception {
        perform(get("/api/v1/stats/activities").param("period", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("ALL"))
                .andExpect(jsonPath("$.data.limit").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].activityId").value("SWITCH-1"))
                .andExpect(jsonPath("$.data.items[0].planId").value(planId))
                .andExpect(jsonPath("$.data.items[0].planTitle").value("성수 나들이"))
                .andExpect(jsonPath("$.data.items[0].triggerType").value("WAITING_TOO_LONG"))
                .andExpect(jsonPath("$.data.items[0].decisionType").value("SWITCH"))
                .andExpect(jsonPath("$.data.items[0].createdAt").exists())
                .andExpect(jsonPath("$.data.items[1].activityId").value("KEEP-" + keepDecisionId))
                .andExpect(jsonPath("$.data.items[1].decisionType").value("KEEP"));
    }

    @Test
    @DisplayName("성공 - 플랜별 활동은 그 플랜의 전환과 유지를 최신순으로 돌려준다")
    void getPlanActivities() throws Exception {
        perform(get("/api/v1/stats/plans/{planId}/activities", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").value(planId))
                .andExpect(jsonPath("$.data.planTitle").value("성수 나들이"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].triggerType").value("WAITING_TOO_LONG"))
                .andExpect(jsonPath("$.data.items[0].decisionType").value("SWITCH"))
                .andExpect(jsonPath("$.data.items[1].decisionType").value("KEEP"));
    }

    @Test
    @DisplayName("성공 - 플랜별 통계는 커밋 뒤 비동기로 갱신되어 전환 1회가 반영된다")
    void getPlanStats() throws Exception {
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> perform(get("/api/v1/stats/plans").param("period", "ALL"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.period").value("ALL"))
                        .andExpect(jsonPath("$.data.items.length()").value(1))
                        .andExpect(jsonPath("$.data.items[0].planId").value(planId))
                        .andExpect(jsonPath("$.data.items[0].planTitle").value("성수 나들이"))
                        .andExpect(jsonPath("$.data.items[0].planDate").value("2026-10-20"))
                        .andExpect(jsonPath("$.data.items[0].region").value("서울"))
                        .andExpect(jsonPath("$.data.items[0].totalSwitches").value(1)));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andDo(print());
    }
}
