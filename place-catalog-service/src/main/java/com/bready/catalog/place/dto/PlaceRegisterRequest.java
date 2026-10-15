package com.bready.catalog.place.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlaceRegisterRequest(
        @NotBlank(message = "외부 장소 ID가 필요합니다.") String externalId,
        @NotBlank(message = "장소 이름이 필요합니다.") String name,
        String address,
        @NotNull(message = "위도가 필요합니다.") BigDecimal latitude,
        @NotNull(message = "경도가 필요합니다.") BigDecimal longitude,
        Boolean isIndoor) {}
