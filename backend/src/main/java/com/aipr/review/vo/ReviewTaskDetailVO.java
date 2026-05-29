package com.aipr.review.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReviewTaskDetailVO {

    private Long taskId;
    private String prUrl;
    private String owner;
    private String repo;
    private Integer pullNumber;
    private String prTitle;
    private String prAuthor;
    private String sourceBranch;
    private String targetBranch;
    private String status;
    private Integer additions;
    private Integer deletions;
    private Integer changedFiles;
    private String summary;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
