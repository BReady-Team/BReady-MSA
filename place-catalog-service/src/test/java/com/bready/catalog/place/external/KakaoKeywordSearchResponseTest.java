package com.bready.catalog.place.external;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoKeywordSearchResponseTest {

    @Test
    @DisplayName("성공 - 외부 ID는 카카오 ID 앞에 kakao- 를 붙인다")
    void externalId_prefix() {
        KakaoKeywordSearchResponse.Document document = document("서울 성동구 도로명", "서울 성동구 지번", "37.544");

        String externalId = document.externalId();

        assertThat(externalId).isEqualTo("kakao-123");
    }

    @Test
    @DisplayName("성공 - 도로명 주소가 없으면 지번 주소를 쓴다")
    void displayAddress_withoutRoadAddress() {
        KakaoKeywordSearchResponse.Document document = document("", "서울 성동구 지번", "37.544");

        String address = document.displayAddress();

        assertThat(address).isEqualTo("서울 성동구 지번");
    }

    @Test
    @DisplayName("성공 - 도로명 주소가 있으면 도로명 주소를 쓴다")
    void displayAddress_withRoadAddress() {
        KakaoKeywordSearchResponse.Document document = document("서울 성동구 도로명", "서울 성동구 지번", "37.544");

        String address = document.displayAddress();

        assertThat(address).isEqualTo("서울 성동구 도로명");
    }

    @Test
    @DisplayName("성공 - 좌표가 숫자가 아니면 null로 바꾼다")
    void latitudeValue_notNumber() {
        KakaoKeywordSearchResponse.Document document = document("서울 성동구 도로명", "서울 성동구 지번", "abc");

        BigDecimal latitude = document.latitudeValue();

        assertThat(latitude).isNull();
    }

    @Test
    @DisplayName("성공 - documents가 null이면 빈 목록이다")
    void documents_null() {
        KakaoKeywordSearchResponse response = new KakaoKeywordSearchResponse(null);

        assertThat(response.documents()).isEmpty();
    }

    private KakaoKeywordSearchResponse.Document document(String roadAddress, String address, String latitude) {
        return new KakaoKeywordSearchResponse.Document("123", "성수 카페", address, roadAddress, "127.055", latitude);
    }
}
