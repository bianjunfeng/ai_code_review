package com.example.aipr.service.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LlmResponse {
    private String content;
    private String finishReason;
}