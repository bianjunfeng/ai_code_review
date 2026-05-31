package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewCommentVO {

    private Long id;
    private Long taskId;
    private String filePath;
    private Integer line;
    private String riskType;
    private String riskLevel;
    private String title;
    private String description;
    private String reason;
    private String evidence;
    private String actionLevel;
    private String suggestion;
    private Double confidence;
    private Boolean needHumanCheck;
}
