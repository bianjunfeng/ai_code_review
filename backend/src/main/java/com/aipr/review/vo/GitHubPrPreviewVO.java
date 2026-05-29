package com.aipr.review.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GitHubPrPreviewVO {

    private String owner;
    private String repo;
    private Integer pullNumber;
    private String title;
    private String body;
    private String author;
    private String sourceBranch;
    private String targetBranch;
    private String state;
    private Integer additions;
    private Integer deletions;
    private Integer changedFiles;
    private List<ChangedFileVO> files;
}
