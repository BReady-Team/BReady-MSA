package com.bready.recommendation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RecommendationApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecommendationApplication.class, args);
    }
}
