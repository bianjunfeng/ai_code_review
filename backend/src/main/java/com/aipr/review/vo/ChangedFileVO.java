package com.aipr.review.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChangedFileVO {

    private String filename;
    private String status;
    private Integer additions;
    private Integer deletions;
    private Integer changes;
    private String patch;
}
