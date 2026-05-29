package com.aipr.review.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewCommentVO {

    private String filePath;
    private Integer lineNumber;
    private String riskType;
    private String severity;
    private String title;
    private String description;
    private String suggestion;
    private Double confidence;
    private Boolean needHumanCheck;
}
