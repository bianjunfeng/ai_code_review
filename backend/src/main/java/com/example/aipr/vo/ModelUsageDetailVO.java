package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ModelUsageDetailVO {

    private Long total;
    private List<ModelUsageLogVO> records;

    @Data
    @Builder
    public static class ModelUsageLogVO {
        private Long id;
        private Long taskId;
        private Long fileId;
        private String skillCode;
        private String provider;
        private String modelName;
        private String callType;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private Long latencyMs;
        private Boolean success;
        private String errorMessage;
        private String createdAt;
    }
}