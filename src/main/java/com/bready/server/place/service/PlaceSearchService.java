package com.bready.server.place.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.place.api.PlaceCategoryType;
import com.bready.server.place.api.PlaceSearchApi;
import com.bready.server.place.api.PlaceSearchResult;
import com.bready.server.place.dto.PlaceSearchResponse;
import com.bready.server.place.dto.kakao.KakaoPlaceDocument;
import com.bready.server.place.exception.PlaceErrorCase;
import com.bready.server.place.external.KakaoPlaceClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceSearchService implements PlaceSearchApi {

    private final KakaoPlaceClient kakaoPlaceClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<PlaceSearchResult> searchPlaces(
            PlaceCategoryType category, String keyword, Double latitude, Double longitude, Integer radius) {
        return search(category, keyword, latitude, longitude, radius).stream()
                .map(place -> new PlaceSearchResult(
                        place.externalId(),
                        place.name(),
                        place.address(),
                        place.latitude(),
                        place.longitude(),
                        place.isIndoor()))
                .toList();
    }

    public List<PlaceSearchResponse> search(
            PlaceCategoryType category, String keyword, Double latitude, Double longitude, Integer radius) {
        String searchKeyword;

        if (keyword != null && !keyword.isBlank()) {
            searchKeyword = keyword + " " + category.getKeyword();
        } else {
            searchKeyword = category.getKeyword();
        }

        log.info("카카오 검색어 = {}", searchKeyword);

        String response =
                kakaoPlaceClient.search(searchKeyword, latitude, longitude, radius, category.getKakaoCategoryCode());

        try {
            JsonNode documents = objectMapper.readTree(response).path("documents");

            List<KakaoPlaceDocument> kakaoPlaces =
                    objectMapper.readerForListOf(KakaoPlaceDocument.class).readValue(documents.toString());

            return kakaoPlaces.stream()
                    .map(p -> PlaceSearchResponse.builder()
                            .externalId("kakao-" + p.getId())
                            .name(p.getPlaceName())
                            .address(
                                    p.getRoadAddressName() != null
                                                    && !p.getRoadAddressName().isBlank()
                                            ? p.getRoadAddressName()
                                            : p.getAddressName())
                            .latitude(toBigDecimal(p.getLatitude()))
                            .longitude(toBigDecimal(p.getLongitude()))
                            .isIndoor(category.isIndoor())
                            .build())
                    .toList();

        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new ApplicationException(PlaceErrorCase.KAKAO_RESPONSE_PARSE_FAILED, e);
        }
    }

    private BigDecimal toBigDecimal(String value) {
        try {
            return value != null && !value.isBlank() ? new BigDecimal(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
