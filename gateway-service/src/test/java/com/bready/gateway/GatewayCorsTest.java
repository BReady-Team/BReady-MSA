package com.bready.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.github.tomakehurst.wiremock.http.RequestMethod;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern;
import static org.assertj.core.api.Assertions.assertThat;

// preflight는 라우트로 가지 않고 Gateway가 직접 답하기 (CORS는 Gateway 한 곳에서 판정)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "MONOLITH_URI=http://localhost:${wiremock.server.port}")
@AutoConfigureWireMock(port = 0)
class GatewayCorsTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String UNKNOWN_ORIGIN = "http://evil.example.com";

    @Autowired
    WebTestClient webTestClient;

    @Test
    @DisplayName("성공 - 허용된 Origin의 preflight는 Gateway가 200으로 답하고 모놀리식으로 보내지 않는다")
    void preflight_allowedOrigin() {
        webTestClient
                .options()
                .uri("/api/v1/plans")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader()
                .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");

        assertThat(findAll(newRequestPattern(RequestMethod.OPTIONS, anyUrl()))).isEmpty();
    }

    @Test
    @DisplayName("실패 - 허용되지 않은 Origin의 preflight는 403")
    void preflight_unknownOrigin() {
        webTestClient
                .options()
                .uri("/api/v1/plans")
                .header(HttpHeaders.ORIGIN, UNKNOWN_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    @DisplayName("성공 - 허용된 Origin의 실제 요청 응답에 Access-Control-Allow-Origin이 한 번만 붙는다")
    void actualRequest_allowedOrigin() {
        stubFor(get(urlEqualTo("/api/v1/plans")).willReturn(aResponse().withStatus(200)));

        webTestClient
                .get()
                .uri("/api/v1/plans")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .values(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, values -> assertThat(values)
                        .containsExactly(ALLOWED_ORIGIN));
    }
}
