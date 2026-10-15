package com.bready.server.place.external;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
@EnableConfigurationProperties(PlaceCatalogProperties.class)
public class PlaceCatalogClientConfig {

    @Bean
    public PlaceCatalogHttpClient placeCatalogHttpClient(
            RestClient.Builder restClientBuilder, PlaceCatalogProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        restClientBuilder.requestFactory(
                ClientHttpRequestFactoryBuilder.detect().build(settings));
        return createClient(restClientBuilder, properties.baseUrl());
    }

    static PlaceCatalogHttpClient createClient(RestClient.Builder restClientBuilder, String baseUrl) {
        RestClient restClient = restClientBuilder.baseUrl(baseUrl).build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(PlaceCatalogHttpClient.class);
    }
}
