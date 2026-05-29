package com.aipr.review.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {

    private String apiBaseUrl = "https://api.github.com";
    private String token;
}
