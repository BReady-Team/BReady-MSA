package com.bready.recommendation.planning.external;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.bready.recommendation.global.client.HttpClientFactory;

@Configuration
public class PlanningClientConfig {

    @Bean
    public PlanningHttpClient planningHttpClient(RestClient.Builder restClientBuilder, PlanningProperties properties) {
        HttpClientFactory.withTimeouts(restClientBuilder, properties.connectTimeout(), properties.readTimeout());
        return HttpClientFactory.createClient(restClientBuilder, properties.baseUrl(), PlanningHttpClient.class);
    }
}
