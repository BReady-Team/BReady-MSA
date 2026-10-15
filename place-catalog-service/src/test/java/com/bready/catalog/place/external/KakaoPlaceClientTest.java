package com.bready.catalog.place.external;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import com.bready.catalog.global.exception.ApplicationException;
import com.bready.catalog.place.exception.PlaceErrorCase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(KakaoPlaceClient.class)
@TestPropertySource(properties = {"kakao.base-url=https://kakao.test", "kakao.rest-api-key=test-key"})
class KakaoPlaceClientTest {

    private static final String DOCUMENTS_BODY =
            """
            {"documents":[{"id":"123","place_name":"성수 카페","address_name":"서울 성동구 지번",
            "road_address_name":"서울 성동구 도로명","x":"127.055","y":"37.544","phone":"02-000"}]}
            """;

    @Autowired
    KakaoPlaceClient kakaoPlaceClient;

    @Autowired
    MockRestServiceServer server;

    @TestConfiguration
    @EnableConfigurationProperties(KakaoProperties.class)
    static class Config {}

    @Test
    @DisplayName("성공 - 검색어·좌표·반경·카테고리 코드와 API 키를 담아 요청하고 장소 목록을 돌려준다")
    void searchByKeyword_success() {
        server.expect(requestTo("https://kakao.test/v2/local/search/keyword.json"
                        + "?query=%EC%84%B1%EC%88%98%20%EC%B9%B4%ED%8E%98&size=15&y=37.544&x=127.055&radius=2000"
                        + "&category_group_code=CE7"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-key"))
                .andRespond(withSuccess(DOCUMENTS_BODY, MediaType.APPLICATION_JSON));

        List<KakaoKeywordSearchResponse.Document> documents = kakaoPlaceClient.searchByKeyword(
                new KakaoKeywordSearchCondition("성수 카페", 37.544, 127.055, 2000, "CE7"));

        server.verify();
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).placeName()).isEqualTo("성수 카페");
    }

    @Test
    @DisplayName("성공 - 좌표가 짝을 이루지 않거나 카테고리 코드가 없으면 그 파라미터를 빼고 요청한다")
    void searchByKeyword_withoutOptionalParameters() {
        server.expect(requestTo("https://kakao.test/v2/local/search/keyword.json"
                        + "?query=%EC%A0%84%EC%8B%9C%EA%B4%80&size=15&radius=2000"))
                .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));

        List<KakaoKeywordSearchResponse.Document> documents =
                kakaoPlaceClient.searchByKeyword(new KakaoKeywordSearchCondition("전시관", 37.544, null, 2000, null));

        server.verify();
        assertThat(documents).isEmpty();
    }

    @Test
    @DisplayName("실패 - 카카오가 4xx를 주면 KAKAO_CLIENT_ERROR")
    void searchByKeyword_clientError() {
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> kakaoPlaceClient.searchByKeyword(condition()))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceErrorCase.KAKAO_CLIENT_ERROR);
    }

    @Test
    @DisplayName("실패 - 카카오가 5xx를 주면 KAKAO_SERVER_ERROR")
    void searchByKeyword_serverError() {
        server.expect(method(HttpMethod.GET)).andRespond(withServerError());

        assertThatThrownBy(() -> kakaoPlaceClient.searchByKeyword(condition()))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceErrorCase.KAKAO_SERVER_ERROR);
    }

    @Test
    @DisplayName("실패 - 응답이 JSON이 아니면 KAKAO_RESPONSE_PARSE_FAILED")
    void searchByKeyword_parseFailed() {
        // given
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("<html>", MediaType.TEXT_HTML));

        // when, then
        assertThatThrownBy(() -> kakaoPlaceClient.searchByKeyword(condition()))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceErrorCase.KAKAO_RESPONSE_PARSE_FAILED);
    }

    private KakaoKeywordSearchCondition condition() {
        return new KakaoKeywordSearchCondition("카페", null, null, 2000, "CE7");
    }
}
