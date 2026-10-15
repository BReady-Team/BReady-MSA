package com.bready.catalog.place.external;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import com.bready.catalog.global.exception.ApplicationException;
import com.bready.catalog.place.exception.PlaceErrorCase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class KakaoPlaceClient {

    private static final String KEYWORD_SEARCH_PATH = "/v2/local/search/keyword.json";
    private static final int PAGE_SIZE = 15;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public KakaoPlaceClient(
            RestClient.Builder restClientBuilder, KakaoProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
                .build();
        this.objectMapper = objectMapper;
    }

    public List<KakaoKeywordSearchResponse.Document> searchByKeyword(KakaoKeywordSearchCondition condition) {
        return parse(requestKeywordSearch(condition)).documents();
    }

    // 카카오가 답한 오류는 502, 연결 실패·타임아웃은 503
    private String requestKeywordSearch(KakaoKeywordSearchCondition condition) {
        try {
            return restClient
                    .get()
                    .uri(uriBuilder -> buildKeywordSearchUri(uriBuilder, condition))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        log.warn(
                                "카카오 장소 검색 4xx status={}",
                                response.getStatusCode().value());
                        throw ApplicationException.from(PlaceErrorCase.KAKAO_CLIENT_ERROR);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        log.error(
                                "카카오 장소 검색 5xx status={}",
                                response.getStatusCode().value());
                        throw ApplicationException.from(PlaceErrorCase.KAKAO_SERVER_ERROR);
                    })
                    .body(String.class);
        } catch (ResourceAccessException e) {
            log.warn("카카오 장소 검색 연결 실패 message={}", e.getMessage());
            throw ApplicationException.from(PlaceErrorCase.KAKAO_UNAVAILABLE, e);
        }
    }

    private URI buildKeywordSearchUri(UriBuilder uriBuilder, KakaoKeywordSearchCondition condition) {
        uriBuilder
                .path(KEYWORD_SEARCH_PATH)
                .queryParam("query", condition.query())
                .queryParam("size", PAGE_SIZE);
        if (condition.hasCoordinate()) {
            uriBuilder.queryParam("y", condition.latitude()).queryParam("x", condition.longitude());
        }
        if (condition.radius() != null) {
            uriBuilder.queryParam("radius", condition.radius());
        }
        if (condition.categoryGroupCode() != null) {
            uriBuilder.queryParam("category_group_code", condition.categoryGroupCode());
        }
        return uriBuilder.build();
    }

    // 본문을 문자열로 받아 직접 파싱
    private KakaoKeywordSearchResponse parse(String body) {
        try {
            return objectMapper.readValue(body, KakaoKeywordSearchResponse.class);
        } catch (JsonProcessingException e) {
            throw ApplicationException.from(PlaceErrorCase.KAKAO_RESPONSE_PARSE_FAILED, e);
        }
    }
}
