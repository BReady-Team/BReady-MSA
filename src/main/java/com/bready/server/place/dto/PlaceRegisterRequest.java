package com.bready.server.place.dto;

import java.math.BigDecimal;

public record PlaceRegisterRequest(
        String externalId, String name, String address, BigDecimal latitude, BigDecimal longitude, Boolean isIndoor) {}
