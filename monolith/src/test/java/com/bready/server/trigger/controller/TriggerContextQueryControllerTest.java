package com.bready.server.trigger.controller;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.bready.server.global.config.security.TestSecurityConfig;
import com.bready.server.global.config.security.jwt.JwtAuthentication;
import com.bready.server.global.exception.ApplicationException;
import com.bready.server.place.api.PlaceCategoryType;
import com.bready.server.trigger.api.CategoryInfo;
import com.bready.server.trigger.api.RepresentativePlace;
import com.bready.server.trigger.api.TriggerContext;
import com.bready.server.trigger.api.TriggerContextApi;
import com.bready.server.trigger.api.TriggerType;
import com.bready.server.trigger.exception.TriggerErrorCase;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 응답 JSON의 필드 이름이 추천 서비스가 읽는 계약
@WebMvcTest(controllers = TriggerContextQueryController.class)
@Import(TestSecurityConfig.class)
class TriggerContextQueryControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long TRIGGER_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TriggerContextApi triggerContextApi;

    private static RequestPostProcessor asUser() {
        return authentication(new JwtAuthentication(USER_ID));
    }

    @Test
    @DisplayName("성공 - 트리거 상황을 종류·지역·현재 카테고리·전체 카테고리로 돌려준다")
    void getTriggerContext_success() throws Exception {
        CategoryInfo meal = new CategoryInfo(3L, PlaceCategoryType.MEAL, 1);
        CategoryInfo cafe = new CategoryInfo(4L, PlaceCategoryType.CAFE, 2);
        given(triggerContextApi.getTriggerContext(USER_ID, TRIGGER_ID))
                .willReturn(
                        new TriggerContext(TRIGGER_ID, TriggerType.WAITING_TOO_LONG, "서울", cafe, List.of(meal, cafe)));

        mockMvc.perform(get("/internal/v1/triggers/{triggerId}/context", TRIGGER_ID)
                        .with(asUser()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.triggerId").value(10))
                .andExpect(jsonPath("$.data.triggerType").value("WAITING_TOO_LONG"))
                .andExpect(jsonPath("$.data.planRegion").value("서울"))
                .andExpect(jsonPath("$.data.currentCategory.categoryId").value(4))
                .andExpect(jsonPath("$.data.currentCategory.categoryType").value("CAFE"))
                .andExpect(jsonPath("$.data.currentCategory.sequence").value(2))
                .andExpect(jsonPath("$.data.planCategories.length()").value(2))
                .andExpect(jsonPath("$.data.planCategories[0].categoryType").value("MEAL"));
    }

    @Test
    @DisplayName("실패 - 트리거가 없으면 TRIGGER_NOT_FOUND")
    void getTriggerContext_notFound() throws Exception {
        given(triggerContextApi.getTriggerContext(USER_ID, TRIGGER_ID))
                .willThrow(ApplicationException.from(TriggerErrorCase.TRIGGER_NOT_FOUND));

        mockMvc.perform(get("/internal/v1/triggers/{triggerId}/context", TRIGGER_ID)
                        .with(asUser()))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(7001));
    }

    @Test
    @DisplayName("실패 - 트리거 ID가 양수가 아니면 400")
    void getTriggerContext_notPositiveId() throws Exception {
        mockMvc.perform(get("/internal/v1/triggers/{triggerId}/context", 0).with(asUser()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").exists());
    }

    @Test
    @DisplayName("성공 - 대표 장소를 외부 ID·주소·좌표로 돌려준다")
    void getRepresentativePlace_success() throws Exception {
        given(triggerContextApi.getRepresentativePlace(USER_ID, TRIGGER_ID))
                .willReturn(new RepresentativePlace(
                        "kakao-1", "서울 성동구", new BigDecimal("37.5445"), new BigDecimal("127.0557")));

        mockMvc.perform(get("/internal/v1/triggers/{triggerId}/representative-place", TRIGGER_ID)
                        .with(asUser()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.externalId").value("kakao-1"))
                .andExpect(jsonPath("$.data.address").value("서울 성동구"))
                .andExpect(jsonPath("$.data.latitude").value(37.5445))
                .andExpect(jsonPath("$.data.longitude").value(127.0557));
    }

    @Test
    @DisplayName("실패 - 대표 후보가 아직 없으면 CATEGORY_STATE_NOT_FOUND")
    void getRepresentativePlace_noRepresentative() throws Exception {
        given(triggerContextApi.getRepresentativePlace(USER_ID, TRIGGER_ID))
                .willThrow(ApplicationException.from(TriggerErrorCase.CATEGORY_STATE_NOT_FOUND));

        mockMvc.perform(get("/internal/v1/triggers/{triggerId}/representative-place", TRIGGER_ID)
                        .with(asUser()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(7005));
    }
}
