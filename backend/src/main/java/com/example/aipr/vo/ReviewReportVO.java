package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReviewReportVO {

    private Long taskId;
    private PrInfoVO prInfo;
    private String summary;
    private Integer riskScore;
    private String riskLevel;
    private List<String> mainChanges;
    private List<RiskItemVO> riskItems;
    private List<String> testSuggestions;
    private String finalReview;
}
