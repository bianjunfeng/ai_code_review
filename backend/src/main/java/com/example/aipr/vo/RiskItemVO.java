package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiskItemVO {

    private String filePath;
    private Integer line;
    private String riskLevel;
    private String riskType;
    private String title;
    private String description;
    private String reason;
    private String evidence;
    private String actionLevel;
    private String suggestion;
    private Double confidence;
    private Boolean needHumanCheck;
}
