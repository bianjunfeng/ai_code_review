package com.example.aipr.service.github;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GitHubPrInfo {

    private String owner;
    private String repo;
    private Integer pullNumber;
    private String title;
    private String description;
    private String author;
    private String sourceBranch;
    private String targetBranch;
    private String state;
    private Integer additions;
    private Integer deletions;
    private Integer changedFiles;
}
