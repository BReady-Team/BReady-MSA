package com.bready.server.place.api;

import java.math.BigDecimal;

// 검색으로 찾은 장소 -> 아직 Catalog에 저장된 장소가 아니라서 id가 없고 외부 ID만 존재
public record PlaceSearchResult(
        String externalId, String name, String address, BigDecimal latitude, BigDecimal longitude, Boolean isIndoor) {}
