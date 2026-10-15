package com.bready.catalog.place.external;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoKeywordSearchResponse(List<Document> documents) {

    private static final String EXTERNAL_ID_PREFIX = "kakao-";

    public KakaoKeywordSearchResponse {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            String id,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("road_address_name") String roadAddressName,
            @JsonProperty("x") String longitude,
            @JsonProperty("y") String latitude) {

        public String externalId() {
            return EXTERNAL_ID_PREFIX + id;
        }

        // 도로명 주소가 없는 장소(공원 등)는 지번 주소를 쓴다
        public String displayAddress() {
            if (roadAddressName == null || roadAddressName.isBlank()) {
                return addressName;
            }
            return roadAddressName;
        }

        public BigDecimal latitudeValue() {
            return toBigDecimal(latitude);
        }

        public BigDecimal longitudeValue() {
            return toBigDecimal(longitude);
        }

        // 좌표가 비거나 숫자가 아니면 그 장소만 좌표 없이 내보낸다
        private static BigDecimal toBigDecimal(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
