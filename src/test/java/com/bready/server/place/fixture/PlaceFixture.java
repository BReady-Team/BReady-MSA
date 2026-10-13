package com.bready.server.place.fixture;

import java.math.BigDecimal;

import org.springframework.test.util.ReflectionTestUtils;

import com.bready.server.place.api.PlaceRegisterRequest;
import com.bready.server.place.domain.Place;

public final class PlaceFixture {

    public static final String EXTERNAL_ID = "kakao-123";
    public static final String NAME = "성수 카페";
    public static final String ADDRESS = "서울 성동구";
    public static final BigDecimal LATITUDE = new BigDecimal("37.544500");
    public static final BigDecimal LONGITUDE = new BigDecimal("127.055700");

    private PlaceFixture() {}

    public static Place savedPlace(Long id) {
        Place place = Place.create(EXTERNAL_ID, NAME, ADDRESS, LATITUDE, LONGITUDE, true);
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }

    public static PlaceRegisterRequest registerRequest() {
        return new PlaceRegisterRequest(EXTERNAL_ID, NAME, ADDRESS, LATITUDE, LONGITUDE, true);
    }
}
