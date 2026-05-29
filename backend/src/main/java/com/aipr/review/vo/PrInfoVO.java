package com.aipr.review.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PrInfoVO {

    private String title;
    private String author;
    private String url;
    private String sourceBranch;
    private String targetBranch;
    private String state;
    private Integer changedFiles;
    private Integer additions;
    private Integer deletions;
}
