package com.example.aipr.vo;

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
    private Integer changedFiles;
    private Integer additions;
    private Integer deletions;
}