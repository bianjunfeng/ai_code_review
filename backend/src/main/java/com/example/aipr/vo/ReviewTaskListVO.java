package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewTaskListVO {

    private Long taskId;
    private String prUrl;
    private String prTitle;
    private String author;
    private String ownerName;
    private String repoName;
    private Integer pullNumber;
    private String sourceBranch;
    private String targetBranch;
    private String status;
    private Integer riskScore;
    private String riskLevel;
    private String modelName;
    private String promptVersion;
    private Boolean cached;
    private Long cachedFromTaskId;
    private String errorMessage;
    private String createdAt;
    private String updatedAt;
}