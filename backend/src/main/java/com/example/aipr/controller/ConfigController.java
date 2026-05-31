package com.example.aipr.controller;

import com.example.aipr.common.Result;
import com.example.aipr.config.AiProperties;
import lombok.Data;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final AiProperties aiProperties;

    public ConfigController(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @GetMapping("/status")
    public Result<ConfigStatusVO> getStatus() {
        ConfigStatusVO vo = new ConfigStatusVO();
        vo.setAiConfigured(aiProperties.getApiKey() != null && !aiProperties.getApiKey().isBlank());
        vo.setAiBaseUrl(aiProperties.getBaseUrl());
        vo.setAiModelName(aiProperties.getModelName());
        vo.setAiTemperature(aiProperties.getTemperature());
        vo.setAiMaxTokens(aiProperties.getMaxTokens());
        vo.setAiPromptVersion(aiProperties.getPromptVersion());
        return Result.ok(vo);
    }

    @Data
    public static class ConfigStatusVO {
        private boolean aiConfigured;
        private String aiBaseUrl;
        private String aiModelName;
        private Double aiTemperature;
        private Integer aiMaxTokens;
        private String aiPromptVersion;
    }
}