package com.example.aipr.service.report;

import com.example.aipr.entity.ReviewComment;
import com.example.aipr.entity.ReviewFile;
import com.example.aipr.entity.ReviewTask;
import com.example.aipr.enums.ReviewTaskStatus;
import com.example.aipr.mapper.ReviewCommentMapper;
import com.example.aipr.mapper.ReviewFileMapper;
import com.example.aipr.service.review.ReviewTaskService;
import com.example.aipr.vo.PrInfoVO;
import com.example.aipr.vo.ReviewMarkdownVO;
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

    public ReviewMarkdownVO getReviewMarkdown(Long taskId) {
        ReviewReportVO report = getReport(taskId);
        return ReviewMarkdownVO.builder()
                .taskId(taskId)
                .markdown(buildMarkdown(report))
                .build();
    }

    private String buildMarkdown(ReviewReportVO report) {
        StringBuilder markdown = new StringBuilder();
        PrInfoVO prInfo = report.getPrInfo();

        appendLine(markdown, "## AI Review 总结");
        appendBlank(markdown);
        appendLine(markdown, "风险等级：" + valueOrDash(report.getRiskLevel()));
        appendLine(markdown, "风险分数：" + (report.getRiskScore() == null ? "-" : report.getRiskScore()));
        appendBlank(markdown);
        appendLine(markdown, "PR：" + valueOrDefault(prInfo == null ? null : prInfo.getTitle(), "未返回标题"));
        appendLine(markdown, "作者：" + valueOrDash(prInfo == null ? null : prInfo.getAuthor()));
        appendBlank(markdown);

        if (hasText(report.getSummary())) {
            appendSection(markdown, "总结");
            appendLine(markdown, report.getSummary());
            appendBlank(markdown);
        }

        if (report.getMainChanges() != null && !report.getMainChanges().isEmpty()) {
            appendSection(markdown, "主要变更");
            report.getMainChanges().forEach(item -> appendLine(markdown, "- " + item));
            appendBlank(markdown);
        }

        appendSection(markdown, "Review 建议");
        if (report.getRiskItems() == null || report.getRiskItems().isEmpty()) {
            appendLine(markdown, "暂无明确风险建议。");
            appendBlank(markdown);
        } else {
            for (int i = 0; i < report.getRiskItems().size(); i++) {
                RiskItemVO item = report.getRiskItems().get(i);
                appendLine(markdown, "#### " + (i + 1) + ". [" + valueOrDefault(item.getRiskLevel(), "INFO") + "] "
                        + valueOrDefault(item.getTitle(), "未命名建议"));
                if (hasText(item.getFilePath())) {
                    appendLine(markdown, "文件：`" + item.getFilePath() + "`");
                }
                if (item.getLine() != null) {
                    appendLine(markdown, "行号：" + item.getLine());
                }
                if (hasText(item.getDescription())) {
                    appendBlank(markdown);
                    appendLine(markdown, item.getDescription());
                }
                if (hasText(item.getReason())) {
                    appendBlank(markdown);
                    appendLine(markdown, "原因：" + item.getReason());
                }
                if (hasText(item.getSuggestion())) {
                    appendBlank(markdown);
                    appendLine(markdown, "建议：" + item.getSuggestion());
                }
                appendBlank(markdown);
            }
        }

        if (report.getTestSuggestions() != null && !report.getTestSuggestions().isEmpty()) {
            appendSection(markdown, "测试建议");
            report.getTestSuggestions().forEach(item -> appendLine(markdown, "- " + item));
            appendBlank(markdown);
        }

        if (hasText(report.getFinalReview())) {
            appendSection(markdown, "最终结论");
            appendLine(markdown, report.getFinalReview());
        }

        return markdown.toString().trim();
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
                .reason(comment.getReason())
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

    private void appendSection(StringBuilder markdown, String title) {
        appendLine(markdown, "### " + title);
        appendBlank(markdown);
    }

    private void appendLine(StringBuilder markdown, String text) {
        markdown.append(text).append('\n');
    }

    private void appendBlank(StringBuilder markdown) {
        markdown.append('\n');
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String valueOrDash(String value) {
        return valueOrDefault(value, "-");
    }

    private String valueOrDefault(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }
}
