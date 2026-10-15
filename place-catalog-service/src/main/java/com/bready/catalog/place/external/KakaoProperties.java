package com.bready.catalog.place.external;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(String baseUrl, String restApiKey) {

    @Override
    public String toString() {
        return "KakaoProperties[baseUrl=" + baseUrl + ", restApiKey=****]";
    }
}
