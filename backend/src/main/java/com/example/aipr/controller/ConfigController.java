package com.example.aipr.controller;

import com.example.aipr.common.Result;
import com.example.aipr.config.AiProperties;
import com.example.aipr.config.GitHubProperties;
import com.example.aipr.config.RateLimitProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;

@Slf4j
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final AiProperties aiProperties;
    private final GitHubProperties githubProperties;
    private final RateLimitProperties rateLimitProperties;
    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    @GetMapping("/status")
    public Result<ConfigStatusVO> getStatus() {
        ConfigStatusVO vo = new ConfigStatusVO();

        // AI 配置
        vo.setAiConfigured(aiProperties.getApiKey() != null && !aiProperties.getApiKey().isBlank());
        vo.setAiBaseUrl(aiProperties.getBaseUrl());
        vo.setAiModelName(aiProperties.getModelName());
        vo.setAiTemperature(aiProperties.getTemperature());
        vo.setAiMaxTokens(aiProperties.getMaxTokens());
        vo.setAiPromptVersion(aiProperties.getPromptVersion());

        // GitHub 配置
        vo.setGithubTokenConfigured(githubProperties.getToken() != null && !githubProperties.getToken().isBlank());
        // 短期展示为"Token 已配置"，P1再做真实连通性检测
        vo.setGithubTokenStatus(githubProperties.getToken() != null && !githubProperties.getToken().isBlank());

        // 数据库连接
        vo.setDatabaseConnected(checkDatabaseConnected());

        // Redis 连接
        vo.setRedisConnected(checkRedisConnected());

        // 缓存和限流
        vo.setCacheEnabled(true); // 缓存始终启用
        vo.setRateLimitEnabled(rateLimitProperties.isEnabled());

        return Result.ok(vo);
    }

    private boolean checkDatabaseConnected() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception e) {
            log.warn("Database connectivity check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkRedisConnected() {
        try {
            redisConnectionFactory.getConnection().ping();
            return true;
        } catch (Exception e) {
            log.warn("Redis connectivity check failed: {}", e.getMessage());
            return false;
        }
    }

    @Data
    public static class ConfigStatusVO {
        private boolean aiConfigured;
        private String aiBaseUrl;
        private String aiModelName;
        private Double aiTemperature;
        private Integer aiMaxTokens;
        private String aiPromptVersion;
        private boolean githubTokenConfigured;
        private boolean githubTokenStatus; // 短期展示为"Token 已配置"，P1再做真实连通性
        private boolean databaseConnected;
        private boolean redisConnected;
        private boolean cacheEnabled;
        private boolean rateLimitEnabled;
    }
}