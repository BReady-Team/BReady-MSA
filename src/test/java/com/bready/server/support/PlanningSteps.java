package com.bready.server.support;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

// 통합 테스트가 HTTP로 밟는 단계 모음
public class PlanningSteps {

    private static final String PASSWORD = "local-test!1";

    private final MockMvc mockMvc;

    public PlanningSteps(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public String signUpAndLogin(String email, String nickname) throws Exception {
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

    public long createPlan(String token) throws Exception {
        MvcResult result = perform(
                post("/api/v1/plans"),
                token,
                """
                        {"title": "성수 나들이", "planDate": "2026-10-20", "region": "서울"}
                        """);
        return readLong(result, "$.data.planId");
    }

    public long createCategory(String token, long planId) throws Exception {
        MvcResult result = perform(
                post("/api/v1/plans/{planId}/categories", planId),
                token,
                """
                        {"categoryType": "CAFE"}
                        """);
        return readLong(result, "$.data.planCategoryId");
    }

    public long createCandidate(String token, long planId, long categoryId, String externalId) throws Exception {
        MvcResult result = perform(
                post("/api/v1/places/candidates"),
                token,
                """
                        {"planId": %d, "categoryId": %d, "externalId": "%s", "name": "%s",
                         "address": "서울 성동구", "latitude": 37.5445, "longitude": 127.0557, "isIndoor": true}
                        """
                        .formatted(planId, categoryId, externalId, externalId));
        return readLong(result, "$.data.candidateId");
    }

    public long createTrigger(String token, long planId, long categoryId) throws Exception {
        MvcResult result = perform(
                post("/api/v1/triggers"),
                token,
                """
                        {"planId": %d, "categoryId": %d, "triggerType": "WAITING_TOO_LONG"}
                        """
                        .formatted(planId, categoryId));
        return readLong(result, "$.data.triggerId");
    }

    public long decideSwitch(String token, long triggerId) throws Exception {
        MvcResult result = perform(
                post("/api/v1/triggers/{triggerId}/decision", triggerId),
                token,
                """
                        {"decisionType": "SWITCH"}
                        """);
        return readLong(result, "$.data.decisionId");
    }

    public MvcResult switchTo(String token, long decisionId, long toCandidateId) throws Exception {
        return perform(
                post("/api/v1/triggers/{decisionId}/switch", decisionId),
                token,
                """
                        {"toCandidateId": %d}
                        """.formatted(toCandidateId));
    }

    public MvcResult setRepresentative(String token, long candidateId) throws Exception {
        return perform(post("/api/v1/places/candidates/{candidateId}/representative", candidateId), token, null);
    }

    public MvcResult deleteCandidate(String token, long candidateId) throws Exception {
        return perform(delete("/api/v1/places/candidates/{candidateId}", candidateId), token, null);
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request).andDo(print()).andReturn();
    }

    private long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }
}
