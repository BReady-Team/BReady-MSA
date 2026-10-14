package com.bready.gateway;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "MONOLITH_URI=http://localhost:${wiremock.server.port}")
@AutoConfigureWireMock(port = 0)
class GatewayRoutingTest {

    private static final String SUCCESS_BODY =
            """
            {"message":"success","data":{"planId":1,"title":"성수 데이트"}}
            """;
    private static final String ERROR_BODY =
            """
            {"errorCode":4001,"message":"플랜을 찾을 수 없습니다."}
            """;

    @Autowired
    WebTestClient webTestClient;

    @Value("${wiremock.server.port}")
    int monolithPort;

    @Test
    @DisplayName("성공 - GET의 경로와 쿼리가 그대로 넘어가고 상태·헤더·본문이 그대로 돌아온다")
    void route_get() {
        stubFor(get(urlEqualTo("/api/v1/plans?page=0&size=10"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(SUCCESS_BODY)));

        webTestClient
                .get()
                .uri("/api/v1/plans?page=0&size=10")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo(SUCCESS_BODY);
    }

    @Test
    @DisplayName("성공 - POST 본문과 Authorization 헤더가 그대로 넘어간다")
    void route_postWithAuthorization() {
        String requestBody =
                """
                {"title":"성수 데이트","planDate":"2026-10-20","region":"서울"}
                """;
        stubFor(post(urlEqualTo("/api/v1/plans")).willReturn(aResponse().withStatus(201)));

        webTestClient
                .post()
                .uri("/api/v1/plans")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus()
                .isCreated();

        List<LoggedRequest> received = findAll(postRequestedFor(urlEqualTo("/api/v1/plans")));
        assertThat(received).hasSize(1);
        assertThat(received.get(0).getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-token");
        assertThat(received.get(0).getBodyAsString()).isEqualTo(requestBody);
    }

    @Test
    @DisplayName("성공 - 모놀리식의 에러 응답을 바꾸지 않고 그대로 돌려준다")
    void route_errorResponse() {
        stubFor(get(urlEqualTo("/api/v1/plans/999"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(ERROR_BODY)));

        webTestClient
                .get()
                .uri("/api/v1/plans/999")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(String.class)
                .isEqualTo(ERROR_BODY);
    }

    @Test
    @DisplayName("성공 - Host는 모놀리식 주소로 바뀌고 X-Forwarded·Forwarded 헤더는 붙지 않는다")
    // trusted-proxies를 설정하지 않아 전달 헤더 필터가 꺼져 있고, 켜면 이 테스트가 깨짐
    void route_forwardedHeaders() {
        stubFor(get(urlPathEqualTo("/api/v1/users/me")).willReturn(aResponse().withStatus(200)));

        webTestClient.get().uri("/api/v1/users/me").exchange().expectStatus().isOk();

        LoggedRequest received =
                findAll(getRequestedFor(urlPathEqualTo("/api/v1/users/me"))).get(0);
        assertThat(received.getHeader(HttpHeaders.HOST)).isEqualTo("localhost:" + monolithPort);
        assertThat(received.containsHeader("X-Forwarded-Host")).isFalse();
        assertThat(received.containsHeader("X-Forwarded-For")).isFalse();
        assertThat(received.containsHeader("Forwarded")).isFalse();
    }
}
