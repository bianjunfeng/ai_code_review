package com.example.aipr.service.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LlmCallContext {
    private Long taskId;
    private Long fileId;
    private String skillCode;
    private String callType;
}