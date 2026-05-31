package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ModelUsageSummaryVO {

    private Long totalCalls;
    private Long successCalls;
    private Long failedCalls;
    private Long totalPromptTokens;
    private Long totalCompletionTokens;
    private Long totalTokens;
    private Double estimatedCost;
    private Double avgLatencyMs;
    private Double successRate;
}