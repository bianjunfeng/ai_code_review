package com.example.aipr.vo;

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
    private String aiSummary;
    private Integer originalPatchLength;
    private Integer analyzedPatchLength;
    private Boolean truncated;
    private Boolean skipped;
    private String skipReason;
}
