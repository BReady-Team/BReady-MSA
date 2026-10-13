package com.bready.server.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.bready.server.support.IntegrationTestSupport;
import com.bready.server.support.PlanningSteps;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("에러 응답 계약 통합 테스트 - 잘못된 period와 빠진 file 파트")
class ErrorResponseContractIntegrationTest extends IntegrationTestSupport {

    private static final int FILE_REQUIRED = 14006;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        token = new PlanningSteps(mockMvc).signUpAndLogin("owner@example.com", "소유자");
    }

    @Test
    @DisplayName("실패 - 통계 요약에 없는 period 값을 주면 본문 없는 400 (INVALID_PERIOD 코드는 나가지 않는다)")
    void statsSummary_invalidPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/stats/summary")
                        .param("period", "WRONG")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("실패 - 최근 활동에 없는 period 값을 주면 본문 없는 400")
    void statsActivities_invalidPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/stats/activities")
                        .param("period", "WRONG")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("실패 - 통계가 아닌 API의 경로 변수 타입이 틀려도 본문 없는 400")
    void plans_typeMismatch() throws Exception {
        mockMvc.perform(get("/api/v1/plans/{planId}", "abc").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("실패 - 파일 업로드에 file 파트가 없으면 FILE_REQUIRED")
    void fileUpload_missingFilePart() throws Exception {
        mockMvc.perform(multipart("/api/v1/files/upload").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(FILE_REQUIRED));
    }

    @Test
    @DisplayName("실패 - 프로필 이미지 변경에 file 파트가 없으면 FILE_REQUIRED")
    void profileImage_missingFilePart() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/users/profile/image")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(FILE_REQUIRED));
    }
}
