package com.example.aipr.service.review;

import com.example.aipr.entity.ReviewComment;
import com.example.aipr.entity.ReviewFile;
import com.example.aipr.entity.ReviewTask;
import com.example.aipr.dto.AiReviewContext;
import com.example.aipr.dto.FileReviewCommentResult;
import com.example.aipr.dto.FileReviewResult;
import com.example.aipr.enums.ReviewTaskStatus;
import com.example.aipr.enums.Severity;
import com.example.aipr.mapper.ReviewCommentMapper;
import com.example.aipr.mapper.ReviewFileMapper;
import com.example.aipr.mapper.ReviewTaskMapper;
import com.example.aipr.service.ai.AiReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewTaskExecutor {

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewFileMapper reviewFileMapper;
    private final ReviewCommentMapper reviewCommentMapper;
    private final AiReviewService aiReviewService;
    private final ObjectMapper objectMapper;

    @Async("reviewAsyncExecutor")
    public void executeAsync(Long taskId) {
        try {
            ReviewTask task = reviewTaskMapper.selectById(taskId);
            if (task == null) {
                log.warn("Review task not found, taskId={}", taskId);
                return;
            }

            updateStatus(taskId, ReviewTaskStatus.REVIEWING, null);
            List<ReviewFile> files = reviewFileMapper.findActiveByTaskId(taskId);
            for (ReviewFile file : files) {
                FileReviewResult result = aiReviewService.reviewFile(buildContext(task, file));
                saveFileReviewResult(taskId, file, result);
            }

            updateStatus(taskId, ReviewTaskStatus.SUMMARIZING, null);
            finishTask(taskId);
        } catch (Exception e) {
            log.warn("Review task failed, taskId={}, message={}", taskId, e.getMessage());
            updateStatus(taskId, ReviewTaskStatus.FAILED, e.getMessage());
        }
    }

    private AiReviewContext buildContext(ReviewTask task, ReviewFile file) {
        return AiReviewContext.builder()
                .prTitle(task.getPrTitle())
                .prAuthor(task.getPrAuthor())
                .sourceBranch(task.getSourceBranch())
                .targetBranch(task.getTargetBranch())
                .filePath(file.getFilePath())
                .fileStatus(file.getFileStatus())
                .language(file.getLanguage())
                .additions(file.getAdditions())
                .deletions(file.getDeletions())
                .changes(file.getChanges())
                .patch(file.getPatch())
                .build();
    }

    private void saveFileReviewResult(Long taskId, ReviewFile file, FileReviewResult result) {
        ReviewFile fileUpdate = new ReviewFile();
        fileUpdate.setId(file.getId());
        fileUpdate.setAiSummary(result.getSummary());
        reviewFileMapper.updateById(fileUpdate);

        List<FileReviewCommentResult> comments = result.getComments();
        if (comments == null || comments.isEmpty()) {
            return;
        }

        List<ReviewComment> entities = comments.stream()
                .map(comment -> toReviewComment(taskId, file, result, comment))
                .toList();
        reviewCommentMapper.insertBatch(entities);
    }

    private ReviewComment toReviewComment(Long taskId,
                                          ReviewFile file,
                                          FileReviewResult result,
                                          FileReviewCommentResult comment) {
        ReviewComment entity = new ReviewComment();
        entity.setTaskId(taskId);
        entity.setFilePath(resolveFilePath(result, file));
        entity.setLineNumber(comment.getLine());
        entity.setRiskType(comment.getRiskType());
        entity.setRiskLevel(comment.getSeverity());
        entity.setTitle(comment.getTitle());
        entity.setDescription(comment.getDescription());
        entity.setSuggestion(comment.getSuggestion());
        entity.setConfidence(toBigDecimal(comment.getConfidence()));
        entity.setNeedHumanCheck(comment.getNeedHumanCheck());
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    private void finishTask(Long taskId) {
        List<ReviewFile> files = reviewFileMapper.findByTaskId(taskId);
        List<ReviewComment> comments = reviewCommentMapper.findByTaskId(taskId);
        RiskSummary riskSummary = summarizeRisk(comments);
        String summary = buildSummary(files);
        String finalReview = buildFinalReview(riskSummary);

        reviewTaskMapper.updateRiskScore(taskId, riskSummary.riskScore(), riskSummary.riskLevel(), summary, finalReview);

        ReviewTask update = new ReviewTask();
        update.setId(taskId);
        update.setStatus(ReviewTaskStatus.SUCCESS.name());
        update.setResultJson(buildResultJson(riskSummary));
        update.setErrorMessage(null);
        update.setUpdatedAt(LocalDateTime.now());
        reviewTaskMapper.updateById(update);
    }

    private RiskSummary summarizeRisk(List<ReviewComment> comments) {
        long high = countSeverity(comments, Severity.HIGH.name());
        long medium = countSeverity(comments, Severity.MEDIUM.name());
        long low = countSeverity(comments, Severity.LOW.name());

        if (high > 0) {
            return new RiskSummary("HIGH", Math.min(100, 70 + (int) high * 10 + (int) medium * 5), high, medium, low);
        }
        if (medium > 0) {
            return new RiskSummary("MEDIUM", Math.min(69, 40 + (int) medium * 8 + (int) low * 3), high, medium, low);
        }
        if (low > 0) {
            return new RiskSummary("LOW", Math.min(39, 20 + (int) low * 4), high, medium, low);
        }
        return new RiskSummary("LOW", 0, high, medium, low);
    }

    private long countSeverity(List<ReviewComment> comments, String severity) {
        return comments.stream()
                .filter(comment -> severity.equals(comment.getRiskLevel()))
                .count();
    }

    private String buildSummary(List<ReviewFile> files) {
        List<String> summaries = files.stream()
                .map(ReviewFile::getAiSummary)
                .filter(Objects::nonNull)
                .filter(summary -> !summary.isBlank())
                .limit(5)
                .toList();
        if (summaries.isEmpty()) {
            return "本次 PR 已完成代码变更分析，未生成明确文件级总结。";
        }
        return "本次 PR 涉及 " + files.size() + " 个文件变更：" + String.join("；", summaries);
    }

    private String buildFinalReview(RiskSummary riskSummary) {
        if ("HIGH".equals(riskSummary.riskLevel())) {
            return "建议优先修复高风险问题后再合并。";
        }
        if ("MEDIUM".equals(riskSummary.riskLevel())) {
            return "建议处理主要中风险问题，并结合人工复核后合并。";
        }
        return "未发现高风险问题，可以结合人工复核后合并。";
    }

    private String buildResultJson(RiskSummary riskSummary) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("riskLevel", riskSummary.riskLevel());
            result.put("riskScore", riskSummary.riskScore());
            result.put("highCount", riskSummary.highCount());
            result.put("mediumCount", riskSummary.mediumCount());
            result.put("lowCount", riskSummary.lowCount());
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return null;
        }
    }

    private void updateStatus(Long taskId, ReviewTaskStatus status, String errorMessage) {
        ReviewTask update = new ReviewTask();
        update.setId(taskId);
        update.setStatus(status.name());
        update.setErrorMessage(errorMessage);
        update.setUpdatedAt(LocalDateTime.now());
        reviewTaskMapper.updateById(update);
    }

    private String resolveFilePath(FileReviewResult result, ReviewFile file) {
        if (result.getFilePath() != null && !result.getFilePath().isBlank()) {
            return result.getFilePath();
        }
        return file.getFilePath();
    }

    private BigDecimal toBigDecimal(Double confidence) {
        return confidence == null ? null : BigDecimal.valueOf(confidence);
    }

    private record RiskSummary(String riskLevel, Integer riskScore, Long highCount, Long mediumCount, Long lowCount) {
    }
}
