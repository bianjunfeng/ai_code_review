package com.example.aipr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class AiPrReviewBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiPrReviewBackendApplication.class, args);
    }
}
