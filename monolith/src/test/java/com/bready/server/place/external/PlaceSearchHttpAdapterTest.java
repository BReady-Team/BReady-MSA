package com.bready.server.place.external;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.place.api.PlaceCategoryType;
import com.bready.server.place.api.PlaceSearchResult;
import com.bready.server.place.exception.PlaceErrorCase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PlaceSearchHttpAdapterTest {

    private static final String BASE_URL = "http://catalog.test";

    private MockRestServiceServer server;
    private PlaceSearchHttpAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new PlaceSearchHttpAdapter(PlaceCatalogClientConfig.createClient(builder, BASE_URL));
    }

    @Test
    @DisplayName("성공 - Catalog 내부 API를 부르고 data의 장소 목록을 돌려준다")
    void searchPlaces_success() {
        server.expect(requestTo(BASE_URL
                        + "/internal/v1/places/search?category=CAFE&keyword=%EC%84%B1%EC%88%98"
                        + "&latitude=37.544&longitude=127.055&radius=2000"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"message":"success","data":[{"externalId":"kakao-1","name":"성수 카페",
                        "address":"서울 성동구","latitude":37.544,"longitude":127.055,"isIndoor":true}]}
                        """,
                        MediaType.APPLICATION_JSON));

        List<PlaceSearchResult> places = adapter.searchPlaces(PlaceCategoryType.CAFE, "성수", 37.544, 127.055, 2000);

        server.verify();
        assertThat(places)
                .containsExactly(new PlaceSearchResult(
                        "kakao-1", "성수 카페", "서울 성동구", new BigDecimal("37.544"), new BigDecimal("127.055"), true));
    }

    @Test
    @DisplayName("성공 - 비어 있는 파라미터는 보내지 않는다")
    void searchPlaces_withoutOptionalParameters() {
        server.expect(requestTo(BASE_URL + "/internal/v1/places/search?category=WALK&radius=2000"))
                .andRespond(withSuccess("{\"message\":\"success\",\"data\":[]}", MediaType.APPLICATION_JSON));

        List<PlaceSearchResult> places = adapter.searchPlaces(PlaceCategoryType.WALK, null, null, null, 2000);

        server.verify();
        assertThat(places).isEmpty();
    }

    @Test
    @DisplayName("실패 - Catalog가 카카오 오류를 돌려주면 상태·코드·문구를 그대로 전달한다")
    void searchPlaces_relayCatalogError() {
        server.expect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorCode\":4104,\"message\":\"카카오 장소 서비스에 장애가 발생했습니다.\"}"));

        assertThatThrownBy(() -> adapter.searchPlaces(PlaceCategoryType.CAFE, null, null, null, 2000))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(new RelayedErrorCase(502, 4104, "카카오 장소 서비스에 장애가 발생했습니다."));
    }

    @Test
    @DisplayName("실패 - 오류 응답이 CommonResponse 모양이 아니면 PLACE_CATALOG_UNAVAILABLE")
    void searchPlaces_unreadableErrorBody() {
        server.expect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html>bad gateway</html>"));

        assertThatThrownBy(() -> adapter.searchPlaces(PlaceCategoryType.CAFE, null, null, null, 2000))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceErrorCase.PLACE_CATALOG_UNAVAILABLE);
    }

    @Test
    @DisplayName("실패 - Catalog에 연결하지 못하면 PLACE_CATALOG_UNAVAILABLE")
    void searchPlaces_connectionFailed() {
        server.expect(method(HttpMethod.GET)).andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> adapter.searchPlaces(PlaceCategoryType.CAFE, null, null, null, 2000))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceErrorCase.PLACE_CATALOG_UNAVAILABLE);
    }
}
