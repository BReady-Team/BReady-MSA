package com.bready.server.place.api;

public interface PlaceCatalogApi {

    // 외부 ID가 같은 장소가 이미 있으면 그 장소를, 없으면 새로 저장해서 돌려준다
    PlaceInfo register(PlaceRegisterRequest request);
}
