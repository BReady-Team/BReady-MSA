package com.bready.server.place.dto;

import java.math.BigDecimal;

import lombok.Builder;

@Builder
public record PlaceSearchResponse(
        String externalId, String name, String address, BigDecimal latitude, BigDecimal longitude, Boolean isIndoor) {}
