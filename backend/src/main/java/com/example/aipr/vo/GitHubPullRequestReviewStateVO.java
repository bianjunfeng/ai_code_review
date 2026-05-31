package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GitHubPullRequestReviewStateVO {

    private String owner;
    private String repo;
    private Integer pullNumber;
    private Boolean reviewed;
    private Long latestTaskId;
    private String latestTaskStatus;
    private String latestRiskLevel;
    private Integer latestRiskScore;
    private Boolean cachedAvailable;
    private String updatedAt;
}
