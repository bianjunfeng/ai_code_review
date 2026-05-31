package com.example.aipr.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiReviewContext {

    private Long taskId;
    private Long fileId;
    private String prTitle;
    private String prDescription;
    private String prAuthor;
    private String sourceBranch;
    private String targetBranch;
    private String filePath;
    private String fileStatus;
    private String language;
    private Integer additions;
    private Integer deletions;
    private Integer changes;
    private String patch;
    private Boolean truncated;
    private List<StaticRuleFinding> staticRuleFindings;
}
