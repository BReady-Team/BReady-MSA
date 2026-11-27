package com.bready.recommendation.planning.external;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bready.recommendation.catalog.api.PlaceCategoryType;
import com.bready.recommendation.global.client.HttpClientFactory;
import com.bready.recommendation.global.exception.ApplicationException;
import com.bready.recommendation.global.exception.RelayedErrorCase;
import com.bready.recommendation.planning.api.CategoryInfo;
import com.bready.recommendation.planning.api.RepresentativePlace;
import com.bready.recommendation.planning.api.TriggerContext;
import com.bready.recommendation.planning.api.TriggerType;
import com.bready.recommendation.planning.exception.PlanningErrorCase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TriggerContextHttpAdapterTest {

    private static final String BASE_URL = "http://monolith.test";
    private static final String AUTHORIZATION = "Bearer user-token";
    private static final Long TRIGGER_ID = 10L;

    private MockRestServiceServer server;
    private TriggerContextHttpAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new TriggerContextHttpAdapter(
                HttpClientFactory.createClient(builder, BASE_URL, PlanningHttpClient.class));
    }

    @Test
    @DisplayName("성공 - 받은 토큰을 Authorization 헤더에 그대로 실어 트리거 상황을 조회한다")
    void getTriggerContext_success() {
        server.expect(requestTo(BASE_URL + "/internal/v1/triggers/10/context"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andRespond(withSuccess(
                        """
                        {"message":"success","data":{"triggerId":10,"triggerType":"WAITING_TOO_LONG","planRegion":"서울",
                        "currentCategory":{"categoryId":4,"categoryType":"CAFE","sequence":2},
                        "planCategories":[{"categoryId":3,"categoryType":"MEAL","sequence":1},
                        {"categoryId":4,"categoryType":"CAFE","sequence":2}]}}
                        """,
                        MediaType.APPLICATION_JSON));

        TriggerContext context = adapter.getTriggerContext(AUTHORIZATION, TRIGGER_ID);

        server.verify();
        CategoryInfo cafe = new CategoryInfo(4L, PlaceCategoryType.CAFE, 2);
        assertThat(context)
                .isEqualTo(new TriggerContext(
                        10L,
                        TriggerType.WAITING_TOO_LONG,
                        "서울",
                        cafe,
                        List.of(new CategoryInfo(3L, PlaceCategoryType.MEAL, 1), cafe)));
    }

    @Test
    @DisplayName("성공 - 받은 토큰을 실어 대표 장소를 조회한다")
    void getRepresentativePlace_success() {
        server.expect(requestTo(BASE_URL + "/internal/v1/triggers/10/representative-place"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andRespond(withSuccess(
                        """
                        {"message":"success","data":{"externalId":"kakao-1","address":"서울 성동구",
                        "latitude":37.5445,"longitude":127.0557}}
                        """,
                        MediaType.APPLICATION_JSON));

        RepresentativePlace place = adapter.getRepresentativePlace(AUTHORIZATION, TRIGGER_ID);

        server.verify();
        assertThat(place)
                .isEqualTo(new RepresentativePlace(
                        "kakao-1", "서울 성동구", new BigDecimal("37.5445"), new BigDecimal("127.0557")));
    }

    @Test
    @DisplayName("실패 - 토큰이 없으면 헤더 없이 부르고, 모놀리식의 401을 401로 전달한다")
    void getTriggerContext_unauthorized() {
        server.expect(headerDoesNotExist(HttpHeaders.AUTHORIZATION)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> adapter.getTriggerContext(null, TRIGGER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(new RelayedErrorCase(401, 401, "로그인이 필요합니다."));
    }

    @Test
    @DisplayName("실패 - 모놀리식이 돌려준 오류는 상태·코드·문구를 그대로 전달한다")
    void getTriggerContext_relayPlanningError() {
        server.expect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorCode\":4002,\"message\":\"플랜에 대한 접근 권한이 없습니다.\"}"));

        assertThatThrownBy(() -> adapter.getTriggerContext(AUTHORIZATION, TRIGGER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(new RelayedErrorCase(403, 4002, "플랜에 대한 접근 권한이 없습니다."));
    }

    @Test
    @DisplayName("실패 - 오류 응답이 CommonResponse 모양이 아니면 PLANNING_UNAVAILABLE")
    void getTriggerContext_unreadableErrorBody() {
        server.expect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html>bad gateway</html>"));

        assertThatThrownBy(() -> adapter.getTriggerContext(AUTHORIZATION, TRIGGER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlanningErrorCase.PLANNING_UNAVAILABLE);
    }

    @Test
    @DisplayName("실패 - 모놀리식에 연결하지 못하면 PLANNING_UNAVAILABLE")
    void getTriggerContext_connectionFailed() {
        server.expect(method(HttpMethod.GET)).andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> adapter.getTriggerContext(AUTHORIZATION, TRIGGER_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlanningErrorCase.PLANNING_UNAVAILABLE);
    }
}
