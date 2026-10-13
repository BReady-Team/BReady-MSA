package com.bready.server.plan.dto;

import com.bready.server.place.api.PlaceInfo;

public record PlaceSummaryResponse(Long id, String externalId, String name, String address, Boolean isIndoor) {

    public static PlaceSummaryResponse from(PlaceInfo place) {
        return new PlaceSummaryResponse(
                place.id(), place.externalId(), place.name(), place.address(), place.isIndoor());
    }
}
