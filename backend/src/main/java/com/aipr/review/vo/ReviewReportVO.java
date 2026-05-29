package com.aipr.review.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReviewReportVO {

    private Long taskId;
    private String status;
    private PrInfoVO prInfo;
    private String summary;
    private Integer riskScore;
    private String riskLevel;
    private List<String> mainChanges;
    private List<RiskItemVO> riskItems;
    private List<ReviewFileVO> files;
    private List<String> testSuggestions;
    private String finalReview;
}
