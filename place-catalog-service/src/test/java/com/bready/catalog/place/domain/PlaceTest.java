package com.bready.catalog.place.domain;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bready.catalog.global.exception.ApplicationException;
import com.bready.catalog.place.exception.PlaceErrorCase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceTest {

    private static final BigDecimal LATITUDE = new BigDecimal("37.5445");
    private static final BigDecimal LONGITUDE = new BigDecimal("127.0557");

    @Test
    @DisplayName("성공 - 외부 ID·이름·좌표가 있으면 장소를 만든다")
    void create_success() {
        Place place = Place.create("kakao-1", "성수 카페", null, LATITUDE, LONGITUDE, true);

        assertThat(place.getExternalId()).isEqualTo("kakao-1");
        assertThat(place.getAddress()).isNull();
    }

    @Test
    @DisplayName("실패 - 외부 ID가 비면 INVALID_PLACE")
    void create_blankExternalId() {
        assertThatThrownBy(() -> Place.create(" ", "성수 카페", null, LATITUDE, LONGITUDE, true))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceErrorCase.INVALID_PLACE);
    }

    @Test
    @DisplayName("실패 - 이름이 비면 INVALID_PLACE")
    void create_blankName() {
        assertThatThrownBy(() -> Place.create("kakao-1", null, null, LATITUDE, LONGITUDE, true))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceErrorCase.INVALID_PLACE);
    }

    @Test
    @DisplayName("실패 - 좌표가 없으면 INVALID_PLACE")
    void create_missingCoordinate() {
        assertThatThrownBy(() -> Place.create("kakao-1", "성수 카페", null, LATITUDE, null, true))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCase")
                .isEqualTo(PlaceErrorCase.INVALID_PLACE);
    }
}
