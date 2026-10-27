package com.bready.catalog.place.dto;

import java.math.BigDecimal;

import com.bready.catalog.place.domain.Place;

public record PlaceRegisterResponse(
        Long id,
        String externalId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean isIndoor) {

    public static PlaceRegisterResponse from(Place place) {
        return new PlaceRegisterResponse(
                place.getId(),
                place.getExternalId(),
                place.getName(),
                place.getAddress(),
                place.getLatitude(),
                place.getLongitude(),
                place.getIsIndoor());
    }
}
