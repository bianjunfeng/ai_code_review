package com.example.aipr.service.review;

import com.example.aipr.config.ReviewProperties;
import com.example.aipr.entity.ReviewComment;
import com.example.aipr.entity.ReviewFile;
import com.example.aipr.entity.ReviewTask;
import com.example.aipr.dto.AiReviewContext;
import com.example.aipr.dto.FileReviewCommentResult;
import com.example.aipr.dto.FileReviewResult;
import com.example.aipr.enums.ReviewTaskStatus;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewTaskExecutor {

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewFileMapper reviewFileMapper;
    private final ReviewCommentMapper reviewCommentMapper;
    private final AiReviewService aiReviewService;
    private final RiskScoreCalculator riskScoreCalculator;
    private final StaticRuleScanner staticRuleScanner;
    private final ObjectMapper objectMapper;
    private final ReviewProperties reviewProperties;
    @org.springframework.beans.factory.annotation.Qualifier("fileReviewExecutor")
    private final Executor fileReviewExecutor;

    @Async("reviewAsyncExecutor")
    public void executeAsync(Long taskId) {
        try {
            ReviewTask task = reviewTaskMapper.selectById(taskId);
            if (task == null) {
                log.warn("[Executor] taskId={}, 任务不存在", taskId);
                return;
            }

            log.info("[Executor] taskId={}, 开始 AI Review, repo={}/{} PR#{}", taskId, task.getOwnerName(), task.getRepoName(), task.getPrNumber());
            updateStatus(taskId, ReviewTaskStatus.REVIEWING, null);

            List<ReviewFile> files = reviewFileMapper.findActiveByTaskId(taskId);
            log.info("[Executor] taskId={}, 待分析文件数={}", taskId, files.size());

            // ── P1：文件级并发评审 + 单文件超时控制 ──
            int timeoutSeconds = reviewProperties.getAi().getFileReviewTimeoutSeconds();
            List<FileReviewResult> aiResults = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (ReviewFile file : files) {
                futures.add(CompletableFuture
                        .supplyAsync(() -> {
                            AiReviewContext context = buildContext(task, file);
                            return aiReviewService.reviewFile(context);
                        }, fileReviewExecutor)
                        .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                        .thenAccept(result -> {
                            saveFileReviewResult(taskId, file, result);
                            aiResults.add(result);
                            successCount.incrementAndGet();
                            log.debug("[Executor] taskId={}, 文件分析完成, file={}, commentCount={}", taskId, file.getFilePath(),
                                    result.getComments() != null ? result.getComments().size() : 0);
                        })
                        .exceptionally(ex -> {
                            failCount.incrementAndGet();
                            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                            log.warn("[Executor] taskId={}, 文件分析失败或超时, file={}, error={}", taskId, file.getFilePath(),
                                    cause.getMessage());
                            markFileFailed(taskId, file, "超时或异常：" + cause.getMessage());
                            return null;
                        }));
            }

            // 等待全部文件完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            int success = successCount.get();
            int fail = failCount.get();
            log.info("[Executor] taskId={}, AI Review 完成, success={}, fail={}", taskId, success, fail);
            updateStatus(taskId, ReviewTaskStatus.SUMMARIZING, null);
            finishTask(taskId, aiResults, fail);
        } catch (Exception e) {
            log.error("[Executor] taskId={}, 任务执行异常: {}", taskId, e.getMessage(), e);
            updateStatus(taskId, ReviewTaskStatus.FAILED, e.getMessage());
        }
    }

    private AiReviewContext buildContext(ReviewTask task, ReviewFile file) {
        var staticRuleFindings = staticRuleScanner.scan(file.getFilePath(), file.getPatch());
        if (!staticRuleFindings.isEmpty()) {
            log.info("[Executor] taskId={}, 静态规则扫描命中, file={}, count={}",
                    task.getId(), file.getFilePath(), staticRuleFindings.size());
        }
        return AiReviewContext.builder()
                .taskId(task.getId())
                .fileId(file.getId())
                .prTitle(task.getPrTitle())
                .prDescription(task.getPrDescription())
                .prAuthor(task.getPrAuthor())
                .sourceBranch(task.getSourceBranch())
                .targetBranch(task.getTargetBranch())
                .commitSummary(task.getCommitSummary())
                .filePath(file.getFilePath())
                .fileStatus(file.getFileStatus())
                .language(file.getLanguage())
                .additions(file.getAdditions())
                .deletions(file.getDeletions())
                .changes(file.getChanges())
                .patch(file.getPatch())
                .truncated(file.getTruncated())
                .staticRuleFindings(staticRuleFindings)
                .build();
    }

    private void markFileFailed(Long taskId, ReviewFile file, String errorMessage) {
        try {
            ReviewFile update = new ReviewFile();
            update.setId(file.getId());
            String msg = errorMessage != null ? errorMessage.substring(0, Math.min(errorMessage.length(), 200)) : "未知错误";
            update.setAiSummary("[分析失败] " + msg);
            reviewFileMapper.updateById(update);
        } catch (Exception e) {
            log.warn("[Executor] taskId={}, 标记文件失败异常, file={}", taskId, file.getFilePath(), e);
        }
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
        entity.setReason(comment.getReason());
        entity.setEvidence(comment.getEvidence());
        entity.setActionLevel(comment.getActionLevel());
        entity.setSuggestion(comment.getSuggestion());
        entity.setConfidence(toBigDecimal(comment.getConfidence()));
        entity.setNeedHumanCheck(comment.getNeedHumanCheck());
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    private void finishTask(Long taskId, List<FileReviewResult> aiResults, int failedFileCount) {
        updateStatus(taskId, ReviewTaskStatus.SCORING, null);

        List<ReviewFile> files = reviewFileMapper.findByTaskId(taskId);
        List<ReviewComment> comments = reviewCommentMapper.findByTaskId(taskId);

        log.info("[Executor] taskId={}, 汇总完成, 文件数={}, 评论数={}", taskId, files.size(), comments.size());

        RiskScoreCalculator.RiskScoreResult riskSummary = riskScoreCalculator.calculate(comments);
        log.info("[Executor] taskId={}, 风险评分计算完成, riskScore={}, riskLevel={}, critical={}, high={}, medium={}, low={}, info={}",
                taskId, riskSummary.riskScore(), riskSummary.riskLevel(),
                riskSummary.criticalCount(), riskSummary.highCount(),
                riskSummary.mediumCount(), riskSummary.lowCount(), riskSummary.infoCount());

        String summary = buildSummary(files, failedFileCount);
        String finalReview = buildFinalReview(riskSummary, failedFileCount);

        reviewTaskMapper.updateRiskScore(taskId, riskSummary.riskScore(), riskSummary.riskLevel(), summary, finalReview);

        ReviewTask update = new ReviewTask();
        update.setId(taskId);
        ReviewTaskStatus finalStatus = failedFileCount > 0
                ? ReviewTaskStatus.PARTIAL_SUCCESS
                : ReviewTaskStatus.SUCCESS;
        update.setStatus(finalStatus.name());
        update.setResultJson(buildResultJson(aiResults));
        update.setErrorMessage(failedFileCount > 0 ? "部分文件分析失败：" + failedFileCount + " 个文件未生成 AI Review 结果" : null);
        update.setUpdatedAt(LocalDateTime.now());
        reviewTaskMapper.updateById(update);

        log.info("[Executor] taskId={}, 任务完成, status={}, riskScore={}, riskLevel={}, failedFiles={}",
                taskId, finalStatus, riskSummary.riskScore(), riskSummary.riskLevel(), failedFileCount);
    }

    private String buildSummary(List<ReviewFile> files, int failedFileCount) {
        List<String> summaries = files.stream()
                .map(ReviewFile::getAiSummary)
                .filter(Objects::nonNull)
                .filter(summary -> !summary.isBlank())
                .filter(summary -> !summary.startsWith("[分析失败]"))
                .limit(5)
                .toList();
        String failedNotice = failedFileCount > 0 ? "其中 " + failedFileCount + " 个文件分析失败，报告可能不完整。" : "";
        if (summaries.isEmpty()) {
            return ("本次 PR 已完成代码变更分析，未生成明确文件级总结。" + failedNotice).trim();
        }
        return ("本次 PR 涉及 " + files.size() + " 个文件变更：" + String.join("；", summaries) + "。" + failedNotice).trim();
    }

    private String buildFinalReview(RiskScoreCalculator.RiskScoreResult riskSummary, int failedFileCount) {
        String partialNotice = failedFileCount > 0 ? "注意：有 " + failedFileCount + " 个文件分析失败，合并前需要人工补充检查。 " : "";
        if ("CRITICAL".equals(riskSummary.riskLevel())) {
            return partialNotice + "本次 PR 存在严重风险，建议修复关键问题并完成人工复核后再考虑合并。";
        }
        if ("HIGH".equals(riskSummary.riskLevel())) {
            return partialNotice + "建议优先修复高风险问题后再合并。";
        }
        if ("MEDIUM".equals(riskSummary.riskLevel())) {
            return partialNotice + "建议处理主要中风险问题，并结合人工复核后合并。";
        }
        return partialNotice + "未发现高风险问题，可以结合人工复核后合并。";
    }

    private String buildResultJson(List<FileReviewResult> aiResults) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("files", aiResults);
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
}
