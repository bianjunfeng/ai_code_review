package com.example.aipr.service.review;

import com.example.aipr.entity.ReviewComment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiskScoreCalculatorTest {

    private final RiskScoreCalculator calculator = new RiskScoreCalculator();

    @Test
    void calculate_emptyComments_returnsLowZero() {
        RiskScoreCalculator.RiskScoreResult result = calculator.calculate(List.of());

        assertEquals(0, result.riskScore());
        assertEquals("LOW", result.riskLevel());
    }

    @Test
    void calculate_multipleLowRiskComments_isStable() {
        List<ReviewComment> comments = List.of(
                comment("LOW", "STYLE", false),
                comment("LOW", "STYLE", false)
        );

        RiskScoreCalculator.RiskScoreResult first = calculator.calculate(comments);
        RiskScoreCalculator.RiskScoreResult second = calculator.calculate(comments);

        assertEquals(16, first.riskScore());
        assertEquals("LOW", first.riskLevel());
        assertEquals(first.riskScore(), second.riskScore());
        assertEquals(first.riskLevel(), second.riskLevel());
    }

    @Test
    void calculate_bugRisk_addsRiskTypeBonus() {
        RiskScoreCalculator.RiskScoreResult result = calculator.calculate(List.of(
                comment("LOW", "BUG_RISK", false)
        ));

        assertEquals(23, result.riskScore());
        assertEquals("LOW", result.riskLevel());
        assertEquals(15, result.riskTypeBonus());
    }

    @Test
    void calculate_needHumanCheckBonus_isCappedAtFifteen() {
        RiskScoreCalculator.RiskScoreResult result = calculator.calculate(List.of(
                comment("INFO", "STYLE", true),
                comment("INFO", "STYLE", true),
                comment("INFO", "STYLE", true),
                comment("INFO", "STYLE", true)
        ));

        assertEquals(15, result.riskScore());
        assertEquals(15, result.humanCheckBonus());
    }

    @Test
    void calculate_riskScore_isCappedAtOneHundred() {
        RiskScoreCalculator.RiskScoreResult result = calculator.calculate(List.of(
                comment("CRITICAL", "SECURITY_RISK", true),
                comment("CRITICAL", "BUG_RISK", true),
                comment("HIGH", "PERFORMANCE_RISK", true)
        ));

        assertEquals(100, result.riskScore());
        assertEquals("CRITICAL", result.riskLevel());
    }

    private ReviewComment comment(String riskLevel, String riskType, boolean needHumanCheck) {
        ReviewComment comment = new ReviewComment();
        comment.setRiskLevel(riskLevel);
        comment.setRiskType(riskType);
        comment.setNeedHumanCheck(needHumanCheck);
        return comment;
    }
}
