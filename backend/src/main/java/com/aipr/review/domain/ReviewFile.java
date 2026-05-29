package com.aipr.review.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReviewFile {

    private Long id;
    private Long taskId;
    private String filePath;
    private String fileStatus;
    private String language;
    private Integer additions;
    private Integer deletions;
    private Integer changes;
    private String patch;
    private String aiSummary;
    private Boolean skipped;
    private String skipReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
