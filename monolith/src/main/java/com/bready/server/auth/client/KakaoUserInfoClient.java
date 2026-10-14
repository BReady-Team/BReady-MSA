package com.bready.server.auth.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.bready.server.auth.dto.KakaoUserInfoResponse;

@Component
public class KakaoUserInfoClient {

    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final WebClient webClient;

    public KakaoUserInfoClient(@Qualifier("kakaoWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public KakaoUserInfoResponse getUserInfo(String kakaoAccessToken) {
        return webClient
                .get()
                .uri(USER_INFO_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                .retrieve()
                .bodyToMono(KakaoUserInfoResponse.class)
                .block();
    }
}
