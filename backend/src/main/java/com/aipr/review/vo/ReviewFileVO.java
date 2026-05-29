package com.aipr.review.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewFileVO {

    private Long fileId;
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
}
