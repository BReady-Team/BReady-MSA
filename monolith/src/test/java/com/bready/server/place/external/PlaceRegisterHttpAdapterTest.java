package com.bready.server.place.external;

import java.io.IOException;
import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.place.api.PlaceInfo;
import com.bready.server.place.api.PlaceRegisterRequest;
import com.bready.server.place.exception.PlaceErrorCase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PlaceRegisterHttpAdapterTest {

    private static final String BASE_URL = "http://catalog.test";
    private static final PlaceRegisterRequest REQUEST = new PlaceRegisterRequest(
            "kakao-1", "성수 카페", "서울 성동구", new BigDecimal("37.544"), new BigDecimal("127.055"), true);

    private MockRestServiceServer server;
    private PlaceRegisterHttpAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new PlaceRegisterHttpAdapter(PlaceCatalogClientConfig.createClient(builder, BASE_URL));
    }

    @Test
    @DisplayName("성공 - Catalog 내부 API에 장소를 보내고 data의 장소를 돌려준다")
    void register_success() {
        server.expect(requestTo(BASE_URL + "/internal/v1/places"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content()
                                .json(
                                        """
                                {"externalId":"kakao-1","name":"성수 카페","address":"서울 성동구",
                                 "latitude":37.544,"longitude":127.055,"isIndoor":true}
                                """))
                .andRespond(withSuccess(
                        """
                        {"message":"success","data":{"id":7,"externalId":"kakao-1","name":"성수 카페",
                        "address":"서울 성동구","latitude":37.544,"longitude":127.055,"isIndoor":true}}
                        """,
                        MediaType.APPLICATION_JSON));

        PlaceInfo place = adapter.register(REQUEST);

        server.verify();
        assertThat(place)
                .isEqualTo(new PlaceInfo(
                        7L, "kakao-1", "성수 카페", "서울 성동구", new BigDecimal("37.544"), new BigDecimal("127.055"), true));
    }

    @Test
    @DisplayName("실패 - Catalog가 장소 검증 오류를 돌려주면 상태·코드·문구를 그대로 전달한다")
    void register_relayCatalogError() {
        server.expect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorCode\":4113,\"message\":\"장소 정보가 올바르지 않습니다.\"}"));

        assertThatThrownBy(() -> adapter.register(REQUEST))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(new RelayedErrorCase(400, 4113, "장소 정보가 올바르지 않습니다."));
    }

    @Test
    @DisplayName("실패 - 오류 응답이 CommonResponse 모양이 아니면 PLACE_CATALOG_UNAVAILABLE")
    void register_unreadableErrorBody() {
        server.expect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html>bad gateway</html>"));

        assertThatThrownBy(() -> adapter.register(REQUEST))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceErrorCase.PLACE_CATALOG_UNAVAILABLE);
    }

    @Test
    @DisplayName("실패 - Catalog에 연결하지 못하면 PLACE_CATALOG_UNAVAILABLE")
    void register_connectionFailed() {
        server.expect(method(HttpMethod.POST)).andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> adapter.register(REQUEST))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceErrorCase.PLACE_CATALOG_UNAVAILABLE);
    }
}
