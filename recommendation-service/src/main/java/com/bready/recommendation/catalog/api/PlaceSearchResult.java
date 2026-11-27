package com.bready.recommendation.catalog.api;

import java.math.BigDecimal;

public record PlaceSearchResult(
        String externalId, String name, String address, BigDecimal latitude, BigDecimal longitude, Boolean isIndoor) {}
