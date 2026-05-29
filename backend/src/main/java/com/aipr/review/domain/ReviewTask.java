package com.aipr.review.domain;

import com.aipr.review.enums.ReviewTaskStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReviewTask {

    private Long id;
    private String prUrl;
    private String owner;
    private String repo;
    private Integer pullNumber;
    private String prTitle;
    private String prDescription;
    private String prAuthor;
    private String sourceBranch;
    private String targetBranch;
    private String prState;
    private Integer additions;
    private Integer deletions;
    private Integer changedFiles;
    private ReviewTaskStatus status;
    private String summary;
    private String mergeSuggestion;
    private Integer highCount;
    private Integer mediumCount;
    private Integer lowCount;
    private Integer infoCount;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
