package com.bready.server.place.api;

import java.math.BigDecimal;

// 경계 밖으로 나가는 장소 정보, 엔티티(Place)는 Place Catalog 밖으로 내보내지 않는다
public record PlaceInfo(
        Long id,
        String externalId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean isIndoor) {}
