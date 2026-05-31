package com.example.aipr.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StaticRuleFinding {

    private String filePath;
    private Integer line;
    private String ruleCode;
    private String ruleName;
    private String riskType;
    private String severity;
    private String message;
    private String evidence;
    private String suggestion;
}
