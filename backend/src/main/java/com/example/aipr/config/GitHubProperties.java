package com.example.aipr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {

    private String token;
    private String apiBaseUrl = "https://api.github.com";
    private Integer connectTimeoutSeconds = 10;
    private Integer readTimeoutSeconds = 30;

    public String getApiBaseUrl() {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            return "https://api.github.com";
        }
        return apiBaseUrl.endsWith("/")
                ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1)
                : apiBaseUrl;
    }
}
