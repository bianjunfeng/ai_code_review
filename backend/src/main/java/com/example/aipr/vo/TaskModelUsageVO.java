package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class TaskModelUsageVO {

    private Long taskId;
    private Long totalCalls;
    private Long successCalls;
    private Long failedCalls;
    private Long totalPromptTokens;
    private Long totalCompletionTokens;
    private Long totalTokens;
    private Double avgLatencyMs;
    private List<ModelUsageDetailVO.ModelUsageLogVO> logs;
}