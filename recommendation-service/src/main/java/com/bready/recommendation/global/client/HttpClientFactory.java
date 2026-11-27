package com.bready.recommendation.global.client;

import java.time.Duration;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

public final class HttpClientFactory {

    private HttpClientFactory() {}

    public static RestClient.Builder withTimeouts(
            RestClient.Builder restClientBuilder, Duration connectTimeout, Duration readTimeout) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);
        return restClientBuilder.requestFactory(
                ClientHttpRequestFactoryBuilder.detect().build(settings));
    }

    public static <T> T createClient(RestClient.Builder restClientBuilder, String baseUrl, Class<T> clientType) {
        RestClient restClient = restClientBuilder.baseUrl(baseUrl).build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(clientType);
    }
}
