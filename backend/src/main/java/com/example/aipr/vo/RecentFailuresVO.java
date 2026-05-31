package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RecentFailuresVO {

    private Long total;
    private List<FailureRecord> records;

    @Data
    @Builder
    public static class FailureRecord {
        private Long taskId;
        private String prTitle;
        private String status;
        private String errorMessage;
        private String createdAt;
    }
}