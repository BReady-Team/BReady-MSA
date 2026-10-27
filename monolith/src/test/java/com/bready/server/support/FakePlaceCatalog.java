package com.bready.server.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.bready.server.place.api.PlaceInfo;
import com.bready.server.place.api.PlaceRegisterRequest;
import com.bready.server.place.external.PlaceCatalogResponse;

public class FakePlaceCatalog {

    private final Map<String, Long> placeIds = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public PlaceCatalogResponse<PlaceInfo> register(PlaceRegisterRequest request) {
        Long placeId = placeIds.computeIfAbsent(request.externalId(), externalId -> sequence.incrementAndGet());
        PlaceInfo place = new PlaceInfo(
                placeId,
                request.externalId(),
                request.name(),
                request.address(),
                request.latitude(),
                request.longitude(),
                request.isIndoor());
        return new PlaceCatalogResponse<>(null, "success", place);
    }
}
