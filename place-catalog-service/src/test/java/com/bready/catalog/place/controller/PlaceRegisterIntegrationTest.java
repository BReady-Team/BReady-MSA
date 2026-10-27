package com.bready.catalog.place.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.bready.catalog.support.IntegrationTestSupport;

import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("장소 등록 내부 API 통합 테스트")
class PlaceRegisterIntegrationTest extends IntegrationTestSupport {

    private static final String REGISTER_URL = "/internal/v1/places";
    private static final String CAFE =
            """
            {"externalId":"kakao-1","name":"성수 카페","address":"서울 성동구",
             "latitude":37.5445,"longitude":127.0557,"isIndoor":true}
            """;

    @Test
    @DisplayName("성공 - 처음 보는 장소를 저장하고 id와 장소 정보를 돌려준다")
    void registerPlace_newPlace() throws Exception {
        register(CAFE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.externalId").value("kakao-1"))
                .andExpect(jsonPath("$.data.name").value("성수 카페"))
                .andExpect(jsonPath("$.data.address").value("서울 성동구"))
                .andExpect(jsonPath("$.data.latitude").value(37.5445))
                .andExpect(jsonPath("$.data.longitude").value(127.0557))
                .andExpect(jsonPath("$.data.isIndoor").value(true));
    }

    @Test
    @DisplayName("성공 - 같은 외부 ID로 다시 등록하면 새로 만들지 않고 같은 id를 돌려준다")
    void registerPlace_samePlaceTwice() throws Exception {
        int firstId = JsonPath.read(register(CAFE).andReturn().getResponse().getContentAsString(), "$.data.id");

        int secondId = JsonPath.read(register(CAFE).andReturn().getResponse().getContentAsString(), "$.data.id");

        assertThat(secondId).isEqualTo(firstId);
        assertThat(jdbcTemplate.queryForObject("select count(*) from places", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("실패 - 이름이 비면 400")
    void registerPlace_blankName() throws Exception {
        register(
                        """
                        {"externalId":"kakao-1","name":"","latitude":37.5445,"longitude":127.0557}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(4001))
                .andExpect(jsonPath("$.message").value("장소 이름이 필요합니다."));
    }

    @Test
    @DisplayName("실패 - 본문이 JSON이 아니면 400")
    void registerPlace_notJson() throws Exception {
        register("not-json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(4001));
    }

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print());
    }
}
