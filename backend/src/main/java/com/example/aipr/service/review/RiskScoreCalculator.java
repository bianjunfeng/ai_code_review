package com.example.aipr.service.review;

import com.example.aipr.entity.ReviewComment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class RiskScoreCalculator {

    private static final int MAX_RISK_SCORE = 100;
    private static final int MAX_HUMAN_CHECK_BONUS = 15;
    private static final int HUMAN_CHECK_BONUS_PER_COMMENT = 5;

    public RiskScoreResult calculate(List<ReviewComment> comments) {
        if (comments == null || comments.isEmpty()) {
            return new RiskScoreResult(0, "LOW", 0L, 0L, 0L, 0L, 0L, 0, 0);
        }

        int baseScore = comments.stream()
                .mapToInt(comment -> levelBaseScore(comment.getRiskLevel()))
                .sum();
        int riskTypeBonus = comments.stream()
                .mapToInt(comment -> riskTypeBonus(comment.getRiskType()))
                .max()
                .orElse(0);
        int humanCheckBonus = Math.min(MAX_HUMAN_CHECK_BONUS,
                (int) comments.stream()
                        .filter(comment -> Boolean.TRUE.equals(comment.getNeedHumanCheck()))
                        .count() * HUMAN_CHECK_BONUS_PER_COMMENT);

        int riskScore = Math.min(MAX_RISK_SCORE, baseScore + riskTypeBonus + humanCheckBonus);

        return new RiskScoreResult(
                riskScore,
                mapRiskLevel(riskScore),
                countLevel(comments, "CRITICAL"),
                countLevel(comments, "HIGH"),
                countLevel(comments, "MEDIUM"),
                countLevel(comments, "LOW"),
                countInfoLevel(comments),
                riskTypeBonus,
                humanCheckBonus
        );
    }

    private int levelBaseScore(String riskLevel) {
        return switch (normalize(riskLevel)) {
            case "CRITICAL" -> 70;
            case "HIGH" -> 40;
            case "MEDIUM" -> 20;
            case "LOW" -> 8;
            case "INFO" -> 0;
            default -> 0;
        };
    }

    private int riskTypeBonus(String riskType) {
        return switch (normalize(riskType)) {
            case "SECURITY", "SECURITY_RISK" -> 20;
            case "BUG_RISK" -> 15;
            case "PERFORMANCE", "PERFORMANCE_RISK" -> 10;
            case "MAINTAINABILITY" -> 5;
            case "STYLE", "INFO" -> 0;
            default -> 0;
        };
    }

    private String mapRiskLevel(int riskScore) {
        if (riskScore <= 30) {
            return "LOW";
        }
        if (riskScore <= 60) {
            return "MEDIUM";
        }
        if (riskScore <= 85) {
            return "HIGH";
        }
        return "CRITICAL";
    }

    private long countLevel(List<ReviewComment> comments, String riskLevel) {
        return comments.stream()
                .filter(comment -> riskLevel.equals(normalize(comment.getRiskLevel())))
                .count();
    }

    private long countInfoLevel(List<ReviewComment> comments) {
        return comments.stream()
                .filter(comment -> {
                    String riskLevel = normalize(comment.getRiskLevel());
                    return !List.of("CRITICAL", "HIGH", "MEDIUM", "LOW").contains(riskLevel);
                })
                .count();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public record RiskScoreResult(Integer riskScore,
                                  String riskLevel,
                                  Long criticalCount,
                                  Long highCount,
                                  Long mediumCount,
                                  Long lowCount,
                                  Long infoCount,
                                  Integer riskTypeBonus,
                                  Integer humanCheckBonus) {
    }
}
