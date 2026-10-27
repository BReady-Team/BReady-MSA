package com.bready.server.trigger.api;

import java.math.BigDecimal;

// 대표 후보가 가리키는 장소 -> 후보에 복사해 둔 스냅샷이라 Place Catalog를 거치지 않는다
public record RepresentativePlace(String externalId, String address, BigDecimal latitude, BigDecimal longitude) {}
