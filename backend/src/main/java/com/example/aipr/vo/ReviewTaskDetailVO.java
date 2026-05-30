package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewTaskDetailVO {

    private Long taskId;
    private String prUrl;
    private String prTitle;
    private String author;
    private String sourceBranch;
    private String targetBranch;
    private String status;
    private Integer riskScore;
    private String riskLevel;
    private String errorMessage;
    private String createdAt;
    private String updatedAt;
}
