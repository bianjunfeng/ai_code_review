package com.aipr.review.service.report;

import com.aipr.review.domain.ReviewComment;
import com.aipr.review.domain.ReviewFile;
import com.aipr.review.domain.ReviewTask;
import com.aipr.review.enums.Severity;
import com.aipr.review.service.review.ReviewTaskService;
import com.aipr.review.service.review.ReviewTaskStore;
import com.aipr.review.vo.PrInfoVO;
import com.aipr.review.vo.ReviewFileVO;
import com.aipr.review.vo.ReviewReportVO;
import com.aipr.review.vo.RiskItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReviewReportService {

    private final ReviewTaskService reviewTaskService;
    private final ReviewTaskStore reviewTaskStore;

    public ReviewReportVO getReport(Long taskId) {
        ReviewTask task = reviewTaskService.findTaskOrThrow(taskId);
        List<ReviewFile> files = reviewTaskStore.findFiles(taskId);
        List<ReviewComment> comments = reviewTaskStore.findComments(taskId);

        return ReviewReportVO.builder()
                .taskId(taskId)
                .status(task.getStatus().name())
                .prInfo(toPrInfoVO(task))
                .summary(Objects.toString(task.getSummary(), "已获取 PR 信息，等待 AI Review 生成总结。"))
                .riskScore(calculateRiskScore(comments))
                .riskLevel(resolveRiskLevel(comments))
                .mainChanges(buildMainChanges(files))
                .riskItems(comments.stream()
                        .sorted(Comparator.comparingInt(this::severityWeight).reversed())
                        .map(this::toRiskItemVO)
                        .toList())
                .files(files.stream()
                        .map(this::toReviewFileVO)
                        .toList())
                .testSuggestions(buildTestSuggestions(comments))
                .finalReview(resolveFinalReview(comments))
                .build();
    }

    private PrInfoVO toPrInfoVO(ReviewTask task) {
        return PrInfoVO.builder()
                .title(task.getPrTitle())
                .author(task.getPrAuthor())
                .url(task.getPrUrl())
                .sourceBranch(task.getSourceBranch())
                .targetBranch(task.getTargetBranch())
                .state(task.getPrState())
                .changedFiles(task.getChangedFiles())
                .additions(task.getAdditions())
                .deletions(task.getDeletions())
                .build();
    }

    private List<String> buildMainChanges(List<ReviewFile> files) {
        return files.stream()
                .limit(8)
                .map(file -> file.getFileStatus() + " " + file.getFilePath()
                        + " (+" + file.getAdditions() + " / -" + file.getDeletions() + ")")
                .toList();
    }

    private List<String> buildTestSuggestions(List<ReviewComment> comments) {
        return comments.stream()
                .filter(comment -> "TEST_RISK".equals(comment.getRiskType()))
                .map(ReviewComment::getSuggestion)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String resolveFinalReview(List<ReviewComment> comments) {
        String riskLevel = resolveRiskLevel(comments);
        if (Severity.HIGH.name().equals(riskLevel)) {
            return "建议优先处理高风险问题后再合并。";
        }
        if (Severity.MEDIUM.name().equals(riskLevel)) {
            return "建议检查中风险问题，并结合人工 Review 决定是否合并。";
        }
        if (comments.isEmpty()) {
            return "AI Review 尚未执行，当前报告仅包含 PR 信息和文件变更。";
        }
        return "当前未发现高优先级风险，可结合人工 Review 继续确认。";
    }

    private int calculateRiskScore(List<ReviewComment> comments) {
        int score = comments.stream()
                .mapToInt(comment -> switch (Objects.toString(comment.getSeverity(), "")) {
                    case "HIGH" -> 30;
                    case "MEDIUM" -> 15;
                    case "LOW" -> 5;
                    default -> 1;
                })
                .sum();
        return Math.min(100, score);
    }

    private String resolveRiskLevel(List<ReviewComment> comments) {
        if (comments.stream().anyMatch(comment -> Severity.HIGH.name().equals(comment.getSeverity()))) {
            return Severity.HIGH.name();
        }
        if (comments.stream().anyMatch(comment -> Severity.MEDIUM.name().equals(comment.getSeverity()))) {
            return Severity.MEDIUM.name();
        }
        if (comments.stream().anyMatch(comment -> Severity.LOW.name().equals(comment.getSeverity()))) {
            return Severity.LOW.name();
        }
        return Severity.LOW.name();
    }

    private int severityWeight(ReviewComment comment) {
        return switch (Objects.toString(comment.getSeverity(), "")) {
            case "HIGH" -> 4;
            case "MEDIUM" -> 3;
            case "LOW" -> 2;
            default -> 1;
        };
    }

    private RiskItemVO toRiskItemVO(ReviewComment comment) {
        return RiskItemVO.builder()
                .filePath(comment.getFilePath())
                .line(comment.getLineNumber())
                .riskLevel(comment.getSeverity())
                .riskType(comment.getRiskType())
                .title(comment.getTitle())
                .description(comment.getDescription())
                .suggestion(comment.getSuggestion())
                .confidence(comment.getConfidence())
                .needHumanCheck(comment.getNeedHumanCheck())
                .build();
    }

    private ReviewFileVO toReviewFileVO(ReviewFile file) {
        return ReviewFileVO.builder()
                .fileId(file.getId())
                .taskId(file.getTaskId())
                .filePath(file.getFilePath())
                .fileStatus(file.getFileStatus())
                .language(file.getLanguage())
                .additions(file.getAdditions())
                .deletions(file.getDeletions())
                .changes(file.getChanges())
                .patch(file.getPatch())
                .aiSummary(file.getAiSummary())
                .skipped(file.getSkipped())
                .skipReason(file.getSkipReason())
                .build();
    }
}
