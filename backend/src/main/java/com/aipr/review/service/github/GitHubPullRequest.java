package com.aipr.review.service.github;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GitHubPullRequest {

    private String owner;
    private String repo;
    private Integer pullNumber;
    private String prUrl;
    private String title;
    private String body;
    private String author;
    private String sourceBranch;
    private String targetBranch;
    private String state;
    private Integer additions;
    private Integer deletions;
    private Integer changedFiles;
    private List<GitHubChangedFile> files;
}
