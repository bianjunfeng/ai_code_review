package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CacheStatisticsVO {

    private Long cacheHits;
    private Long cacheMisses;
    private Double cacheHitRate;
    private Long savedModelCalls;
    private Long savedTokensEstimate;
}