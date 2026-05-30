package com.example.aipr.service.report;

import com.example.aipr.entity.ReviewComment;
import com.example.aipr.entity.ReviewFile;
import com.example.aipr.entity.ReviewTask;
import com.example.aipr.enums.ReviewTaskStatus;
import com.example.aipr.mapper.ReviewCommentMapper;
import com.example.aipr.mapper.ReviewFileMapper;
import com.example.aipr.service.review.ReviewTaskService;
import com.example.aipr.vo.PrInfoVO;
import com.example.aipr.vo.ReviewReportVO;
import com.example.aipr.vo.RiskItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReviewReportService {

    private final ReviewTaskService reviewTaskService;
    private final ReviewFileMapper reviewFileMapper;
    private final ReviewCommentMapper reviewCommentMapper;

    public ReviewReportVO getReport(Long taskId) {
        ReviewTask task = reviewTaskService.requireTask(taskId);
        List<ReviewFile> files = reviewFileMapper.findByTaskId(taskId);
        List<ReviewComment> comments = reviewCommentMapper.findByTaskId(taskId);

        return ReviewReportVO.builder()
                .taskId(taskId)
                .prInfo(PrInfoVO.builder()
                        .title(task.getPrTitle())
                        .author(task.getPrAuthor())
                        .url(task.getPrUrl())
                        .sourceBranch(task.getSourceBranch())
                        .targetBranch(task.getTargetBranch())
                        .changedFiles(files.size())
                        .additions(sumAdditions(files))
                        .deletions(sumDeletions(files))
                        .build())
                .summary(resolveSummary(task))
                .riskScore(task.getRiskScore() == null ? 0 : task.getRiskScore())
                .riskLevel(task.getRiskLevel() == null ? "LOW" : task.getRiskLevel())
                .mainChanges(buildMainChanges(files))
                .riskItems(comments.stream().map(this::toRiskItem).toList())
                .testSuggestions(buildTestSuggestions(comments))
                .finalReview(resolveFinalReview(task))
                .build();
    }

    private String resolveSummary(ReviewTask task) {
        if (task.getSummary() != null && !task.getSummary().isBlank()) {
            return task.getSummary();
        }
        if (ReviewTaskStatus.FAILED.name().equals(task.getStatus())) {
            return "AI Review 任务执行失败：" + task.getErrorMessage();
        }
        if (!ReviewTaskStatus.SUCCESS.name().equals(task.getStatus())) {
            return "AI Review 任务正在执行中，请稍后刷新报告。";
        }
        return "本次 PR 暂无明确总结。";
    }

    private List<String> buildMainChanges(List<ReviewFile> files) {
        List<String> summaries = files.stream()
                .map(ReviewFile::getAiSummary)
                .filter(Objects::nonNull)
                .filter(summary -> !summary.isBlank())
                .limit(5)
                .toList();
        if (!summaries.isEmpty()) {
            return summaries;
        }

        return files.stream()
                .map(file -> file.getFilePath() + "（" + file.getFileStatus() + "）")
                .limit(5)
                .toList();
    }

    private RiskItemVO toRiskItem(ReviewComment comment) {
        BigDecimal confidence = comment.getConfidence();
        return RiskItemVO.builder()
                .filePath(comment.getFilePath())
                .line(comment.getLineNumber())
                .riskLevel(comment.getRiskLevel())
                .riskType(comment.getRiskType())
                .title(comment.getTitle())
                .description(comment.getDescription())
                .suggestion(comment.getSuggestion())
                .confidence(confidence == null ? null : confidence.doubleValue())
                .needHumanCheck(comment.getNeedHumanCheck())
                .build();
    }

    private List<String> buildTestSuggestions(List<ReviewComment> comments) {
        List<String> suggestions = comments.stream()
                .filter(comment -> "TEST_RISK".equals(comment.getRiskType()))
                .map(ReviewComment::getSuggestion)
                .filter(Objects::nonNull)
                .filter(suggestion -> !suggestion.isBlank())
                .toList();
        if (!suggestions.isEmpty()) {
            return suggestions;
        }
        if (comments.isEmpty()) {
            return List.of();
        }
        return List.of("建议根据本次 PR 变更补充核心路径和异常分支测试。");
    }

    private String resolveFinalReview(ReviewTask task) {
        if (task.getFinalReview() != null && !task.getFinalReview().isBlank()) {
            return task.getFinalReview();
        }
        if (ReviewTaskStatus.FAILED.name().equals(task.getStatus())) {
            return "任务失败，请修复配置或外部服务问题后重新评审。";
        }
        return "任务仍在执行或暂无明确结论，请稍后刷新。";
    }

    private Integer sumAdditions(List<ReviewFile> files) {
        return files.stream()
                .map(ReviewFile::getAdditions)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);
    }

    private Integer sumDeletions(List<ReviewFile> files) {
        return files.stream()
                .map(ReviewFile::getDeletions)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);
    }
}
