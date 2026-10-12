package com.bready.server.integration;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bready.server.support.IntegrationTestSupport;

import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 한 사용자가 가입해서 전환을 실행하기까지의 흐름을 HTTP부터 MySQL까지 사이클 검증
class PlanningFlowIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "local-test!1";
    private static final int ACCESS_DENIED = 4002;
    private static final int DUPLICATE_PLACE_CANDIDATE = 4106;

    @Test
    @DisplayName("성공 - 가입부터 전환까지 진행하면 대표 후보가 바뀌고 통계에 전환 1회가 반영")
    void switchFlow_success() throws Exception {
        String token = signUpAndLogin("owner@example.com", "소유자");
        long planId = createPlan(token);
        long categoryId = createCategory(token, planId);
        long firstCandidateId = createCandidate(token, planId, categoryId, "kakao-1", "첫번째카페");
        long secondCandidateId = createCandidate(token, planId, categoryId, "kakao-2", "두번째카페");
        assertThat(representativeCandidateId(token, planId)).isEqualTo(firstCandidateId);

        long triggerId = createTrigger(token, planId, categoryId);
        long decisionId = decideSwitch(token, triggerId);
        MvcResult switched = perform(
                post("/api/v1/triggers/{decisionId}/switch", decisionId),
                token,
                """
                        {"toCandidateId": %d}
                        """
                        .formatted(secondCandidateId));

        assertThat(switched.getResponse().getStatus()).isEqualTo(200);
        assertThat(readLong(switched, "$.data.fromCandidateId")).isEqualTo(firstCandidateId);
        assertThat(readLong(switched, "$.data.toCandidateId")).isEqualTo(secondCandidateId);
        assertThat(representativeCandidateId(token, planId)).isEqualTo(secondCandidateId);
        // 통계는 커밋 뒤 비동기 리스너가 갱신하므로 기다림
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(totalSwitches(token)).isEqualTo(1L));
    }

    @Test
    @DisplayName("실패 - 토큰 없이 플랜을 조회하면 401")
    void getPlanDetail_withoutToken() throws Exception {
        String token = signUpAndLogin("owner@example.com", "소유자");
        long planId = createPlan(token);

        mockMvc.perform(get("/api/v1/plans/{planId}", planId)).andDo(print()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("실패 - 남의 플랜을 조회하면 PLAN_ACCESS_DENIED")
    void getPlanDetail_notOwner() throws Exception {
        String ownerToken = signUpAndLogin("owner@example.com", "소유자");
        String otherToken = signUpAndLogin("other@example.com", "다른사람");
        long planId = createPlan(ownerToken);

        mockMvc.perform(get("/api/v1/plans/{planId}", planId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ACCESS_DENIED));
    }

    @Test
    @DisplayName("실패 - 같은 장소를 같은 카테고리에 두 번 등록하면 DUPLICATE_PLACE_CANDIDATE")
    void createCandidate_duplicate() throws Exception {
        String token = signUpAndLogin("owner@example.com", "소유자");
        long planId = createPlan(token);
        long categoryId = createCategory(token, planId);
        createCandidate(token, planId, categoryId, "kakao-1", "첫번째카페");

        MvcResult result = perform(
                post("/api/v1/places/candidates"), token, candidateBody(planId, categoryId, "kakao-1", "첫번째카페"));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(readLong(result, "$.errorCode")).isEqualTo(DUPLICATE_PLACE_CANDIDATE);
    }

    private String signUpAndLogin(String email, String nickname) throws Exception {
        perform(
                post("/api/v1/auth/signup"),
                null,
                """
                        {"nickname": "%s", "email": "%s", "password": "%s"}
                        """
                        .formatted(nickname, email, PASSWORD));
        MvcResult login = perform(
                post("/api/v1/auth/login"),
                null,
                """
                        {"email": "%s", "password": "%s"}
                        """
                        .formatted(email, PASSWORD));
        return JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private long createPlan(String token) throws Exception {
        MvcResult result = perform(
                post("/api/v1/plans"),
                token,
                """
                        {"title": "성수 나들이", "planDate": "2026-10-20", "region": "서울"}
                        """);
        return readLong(result, "$.data.planId");
    }

    private long createCategory(String token, long planId) throws Exception {
        MvcResult result = perform(
                post("/api/v1/plans/{planId}/categories", planId),
                token,
                """
                        {"categoryType": "CAFE"}
                        """);
        return readLong(result, "$.data.planCategoryId");
    }

    private long createCandidate(String token, long planId, long categoryId, String externalId, String name)
            throws Exception {
        MvcResult result =
                perform(post("/api/v1/places/candidates"), token, candidateBody(planId, categoryId, externalId, name));
        return readLong(result, "$.data.candidateId");
    }

    private String candidateBody(long planId, long categoryId, String externalId, String name) {
        return """
                {"planId": %d, "categoryId": %d, "externalId": "%s", "name": "%s",
                 "address": "서울 성동구", "latitude": 37.5445, "longitude": 127.0557, "isIndoor": true}
                """
                .formatted(planId, categoryId, externalId, name);
    }

    private long createTrigger(String token, long planId, long categoryId) throws Exception {
        MvcResult result = perform(
                post("/api/v1/triggers"),
                token,
                """
                        {"planId": %d, "categoryId": %d, "triggerType": "WAITING_TOO_LONG"}
                        """
                        .formatted(planId, categoryId));
        return readLong(result, "$.data.triggerId");
    }

    private long decideSwitch(String token, long triggerId) throws Exception {
        MvcResult result = perform(
                post("/api/v1/triggers/{triggerId}/decision", triggerId),
                token,
                """
                        {"decisionType": "SWITCH"}
                        """);
        return readLong(result, "$.data.decisionId");
    }

    private long representativeCandidateId(String token, long planId) throws Exception {
        MvcResult result = perform(get("/api/v1/plans/{planId}", planId), token, null);
        return readLong(result, "$.data.categories[0].representativeCandidateId");
    }

    private long totalSwitches(String token) throws Exception {
        MvcResult result = perform(get("/api/v1/stats/plans").param("period", "ALL"), token, null);
        return readLong(result, "$.data.items[0].totalSwitches");
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request).andDo(print()).andReturn();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }
}
