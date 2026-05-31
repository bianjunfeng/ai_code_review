package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewTaskStatisticsVO {

    private Long totalTasks;
    private Long todayTasks;
    private Long successTasks;
    private Long failedTasks;
    private Long runningTasks;
    private Long highRiskTasks;
    private Long mediumRiskTasks;
    private Long lowRiskTasks;
    private Long cacheHits;
    private Double avgDurationMs;
}
