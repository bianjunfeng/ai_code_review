package com.example.aipr.service.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LlmMessage {
    private String role;
    private String content;
}