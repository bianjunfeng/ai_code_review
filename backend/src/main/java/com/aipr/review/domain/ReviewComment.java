package com.aipr.review.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReviewComment {

    private Long id;
    private Long taskId;
    private Long fileId;
    private String filePath;
    private Integer lineNumber;
    private String riskType;
    private String severity;
    private String title;
    private String description;
    private String suggestion;
    private Double confidence;
    private Boolean needHumanCheck;
    private LocalDateTime createdAt;
}
