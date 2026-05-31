package com.example.aipr.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * PR 级别总结结果，由 LLM 生成。
 */
@Data
@Builder
public class PrSummaryResult {

    /** PR 总体变更摘要。 */
    private String summary;

    /** 最终评审结论。 */
    private String finalReview;

    /** 测试建议列表。 */
    private List<String> testSuggestions;
}