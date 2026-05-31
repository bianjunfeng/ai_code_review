package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GitHubPullRequestVO {

    private String owner;
    private String repo;
    private Integer pullNumber;
    private String title;
    private String author;
    private String state;
    private String sourceBranch;
    private String targetBranch;
    private String htmlUrl;
    private String createdAt;
    private String updatedAt;
    private Boolean draft;
    private Boolean reviewed;
    private Long latestTaskId;
    private String latestTaskStatus;
    private String latestRiskLevel;
    private Integer latestRiskScore;
    private Boolean cachedAvailable;
}
