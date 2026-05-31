package com.example.aipr.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FileReviewCommentResult {

    private Integer line;
    private String riskType;
    private String severity;
    private String title;
    private String description;
    private String reason;
    private String evidence;
    private String actionLevel;
    private String suggestion;
    private Double confidence;
    private Boolean needHumanCheck;
}
