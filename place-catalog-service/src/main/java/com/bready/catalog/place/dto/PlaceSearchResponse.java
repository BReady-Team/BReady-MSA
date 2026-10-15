package com.bready.catalog.place.dto;

import java.math.BigDecimal;

import com.bready.catalog.place.external.KakaoKeywordSearchResponse;

public record PlaceSearchResponse(
        String externalId, String name, String address, BigDecimal latitude, BigDecimal longitude, Boolean isIndoor) {

    public static PlaceSearchResponse of(KakaoKeywordSearchResponse.Document document, boolean indoor) {
        return new PlaceSearchResponse(
                document.externalId(),
                document.placeName(),
                document.displayAddress(),
                document.latitudeValue(),
                document.longitudeValue(),
                indoor);
    }
}
