package com.bready.catalog.place.domain;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.bready.catalog.global.entity.BaseEntity;
import com.bready.catalog.global.exception.ApplicationException;
import com.bready.catalog.place.exception.PlaceErrorCase;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "places",
        uniqueConstraints = @UniqueConstraint(name = "uk_places_external_id", columnNames = "external_id"))
public class Place extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 카카오 장소 ID에 kakao- 를 붙인 값
    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(precision = 10, scale = 6, nullable = false)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 6, nullable = false)
    private BigDecimal longitude;

    @Column(name = "is_indoor")
    private Boolean isIndoor;

    public static Place create(
            String externalId,
            String name,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            Boolean isIndoor) {
        if (isBlank(externalId) || isBlank(name) || latitude == null || longitude == null) {
            throw ApplicationException.from(PlaceErrorCase.INVALID_PLACE);
        }
        Place place = new Place();
        place.externalId = externalId;
        place.name = name;
        place.address = address;
        place.latitude = latitude;
        place.longitude = longitude;
        place.isIndoor = isIndoor;
        return place;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
