package com.example.aipr.service.ai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LlmRequest {
    private String model;
    private List<LlmMessage> messages;
    private Double temperature;
    private Integer maxTokens;
}