package com.example.aipr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private CreateReview createReview = new CreateReview();
    private ForceRefresh forceRefresh = new ForceRefresh();

    @Data
    public static class CreateReview {
        private int ipLimit = 5;
        private long ipWindowSeconds = 60;
        private int prLimit = 1;
        private long prWindowSeconds = 30;
    }

    @Data
    public static class ForceRefresh {
        private int limit = 1;
        private long windowSeconds = 300;
    }
}