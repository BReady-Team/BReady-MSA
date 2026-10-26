package com.bready.server.plan.domain;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// 후보를 추가할 때 받은 장소 정보의 복사본
@Embeddable
public record PlaceSnapshot(
        @Column(name = "place_external_id", nullable = false) String externalId,
        @Column(name = "place_name", nullable = false) String name,
        @Column(name = "place_address") String address,
        @Column(name = "place_latitude", precision = 10, scale = 6, nullable = false) BigDecimal latitude,
        @Column(name = "place_longitude", precision = 10, scale = 6, nullable = false) BigDecimal longitude,
        @Column(name = "place_is_indoor") Boolean isIndoor) {}
