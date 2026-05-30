package com.example.aipr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String baseUrl = "https://api.deepseek.com";
    private String apiKey;
    private String modelName = "deepseek-chat";
    private Double temperature = 0.2;
    private Integer maxTokens = 3000;
    private String promptVersion = "v1";

    public String getBaseUrl() {
        if (baseUrl == null) {
            return "https://api.deepseek.com";
        }
        if (!baseUrl.endsWith("/")) {
            return baseUrl + "/";
        }
        return baseUrl;
    }
}