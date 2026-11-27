package com.bready.recommendation.catalog.external;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.bready.recommendation.global.client.HttpClientFactory;

@Configuration
public class PlaceCatalogClientConfig {

    @Bean
    public PlaceCatalogHttpClient placeCatalogHttpClient(
            RestClient.Builder restClientBuilder, PlaceCatalogProperties properties) {
        HttpClientFactory.withTimeouts(restClientBuilder, properties.connectTimeout(), properties.readTimeout());
        return HttpClientFactory.createClient(restClientBuilder, properties.baseUrl(), PlaceCatalogHttpClient.class);
    }
}
