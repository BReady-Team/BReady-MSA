package com.bready.server.place.api;

import java.util.Collection;
import java.util.List;

public interface PlaceCatalogApi {

    // 외부 ID가 같은 장소가 이미 있으면 그 장소를, 없으면 새로 저장해서 돌려준다
    PlaceInfo register(PlaceRegisterRequest request);

    // 없는 ID는 결과에서 빠지고 순서는 보장하지 않는다
    List<PlaceInfo> getPlaces(Collection<Long> placeIds);
}
