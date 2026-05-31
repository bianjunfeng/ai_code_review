package com.example.aipr.service.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aipr.common.BusinessException;
import com.example.aipr.config.AiProperties;
import com.example.aipr.config.RateLimitProperties;
import com.example.aipr.entity.ReviewComment;
import com.example.aipr.entity.ReviewFile;
import com.example.aipr.entity.ReviewTask;
import com.example.aipr.enums.ErrorCode;
import com.example.aipr.enums.ReviewTaskStatus;
import com.example.aipr.mapper.ReviewCommentMapper;
import com.example.aipr.mapper.ReviewFileMapper;
import com.example.aipr.mapper.ReviewTaskMapper;
import com.example.aipr.service.github.GitHubChangedFile;
import com.example.aipr.service.github.GitHubClient;
import com.example.aipr.service.github.GitHubCommitInfo;
import com.example.aipr.service.github.GitHubPrInfo;
import com.example.aipr.service.github.ParsedPrUrl;
import com.example.aipr.service.github.PrUrlParser;
import com.example.aipr.service.prompt.PromptRenderer;
import com.example.aipr.service.ratelimit.RateLimitService;
import com.example.aipr.vo.ReviewCommentVO;
import com.example.aipr.vo.ReviewFileVO;
import com.example.aipr.vo.ReviewTaskCreatedVO;
import com.example.aipr.vo.ReviewTaskDetailVO;
import com.example.aipr.vo.ReviewTaskListVO;
import com.example.aipr.vo.ReviewTaskPageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewTaskService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PrUrlParser prUrlParser;
    private final GitHubClient gitHubClient;
    private final PromptRenderer promptRenderer;
    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewFileMapper reviewFileMapper;
    private final ReviewCommentMapper reviewCommentMapper;
    private final ReviewTaskExecutor reviewTaskExecutor;
    private final DiffPreprocessor diffPreprocessor;
    private final AiProperties aiProperties;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;

    public ReviewTaskCreatedVO createTask(String prUrl, Boolean forceRefresh) {
        log.info("[Task] 创建 Review 任务, prUrl={}, forceRefresh={}", prUrl, forceRefresh);
        ParsedPrUrl parsedPrUrl = prUrlParser.parse(prUrl);

        try {
            log.info("[Task] 解析 PR 信息, repo={}/{} PR#{}", parsedPrUrl.owner(), parsedPrUrl.repo(), parsedPrUrl.pullNumber());
            GitHubPrInfo prInfo = gitHubClient.getPullRequest(parsedPrUrl);
            log.info("[Task] PR 信息获取成功, title={}, author={}, source={} -> {}", prInfo.getTitle(), prInfo.getAuthor(), prInfo.getSourceBranch(), prInfo.getTargetBranch());

            // 检查缓存
            if (!Boolean.TRUE.equals(forceRefresh)) {
                ReviewTask cachedTask = findCachedTask(parsedPrUrl, prInfo);
                if (cachedTask != null) {
                    log.info("[Task] 缓存命中, cachedTaskId={}, riskScore={}, riskLevel={}", cachedTask.getId(), cachedTask.getRiskScore(), cachedTask.getRiskLevel());
                    // 创建新的缓存命中记录，而不是直接返回原任务
                    ReviewTask cacheHitTask = createCacheHitTask(cachedTask, parsedPrUrl, prInfo);
                    return ReviewTaskCreatedVO.builder()
                            .taskId(cacheHitTask.getId())
                            .status(cacheHitTask.getStatus())
                            .cached(true)
                            .cachedFromTaskId(cachedTask.getId())
                            .build();
                }
            }

            // forceRefresh 限流
            if (Boolean.TRUE.equals(forceRefresh) && rateLimitProperties.isEnabled()) {
                String forceKey = String.format("rate_limit:force_refresh:%s:%s:%d",
                        parsedPrUrl.owner(), parsedPrUrl.repo(), parsedPrUrl.pullNumber());
                boolean allowed = rateLimitService.tryAcquire(
                        forceKey,
                        rateLimitProperties.getForceRefresh().getLimit(),
                        rateLimitProperties.getForceRefresh().getWindowSeconds()
                );
                if (!allowed) {
                    throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "该 PR 刚刚重新分析过，请稍后再试");
                }
            }

            // PR 级限流（未命中缓存时）
            if (rateLimitProperties.isEnabled()) {
                String prKey = String.format("rate_limit:pr:%s:%s:%d",
                        parsedPrUrl.owner(), parsedPrUrl.repo(), parsedPrUrl.pullNumber());
                boolean allowed = rateLimitService.tryAcquire(
                        prKey,
                        rateLimitProperties.getCreateReview().getPrLimit(),
                        rateLimitProperties.getCreateReview().getPrWindowSeconds()
                );
                if (!allowed) {
                    throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "同一个 PR 正在处理中，请稍后再试");
                }
            }

            ReviewTask task = createPendingTask(prUrl, parsedPrUrl, prInfo);

            log.info("[Task] taskId={}, 开始获取 Diff 文件", task.getId());
            List<GitHubChangedFile> changedFiles = gitHubClient.getPullRequestFiles(parsedPrUrl);
            saveChangedFiles(task.getId(), changedFiles);
            log.info("[Task] taskId={}, Diff 文件保存完成, fileCount={}", task.getId(), changedFiles.size());

            updateStatus(task.getId(), ReviewTaskStatus.PENDING, null);
            log.info("[Task] taskId={}, 任务已提交异步执行", task.getId());
            reviewTaskExecutor.executeAsync(task.getId());

            return ReviewTaskCreatedVO.builder()
                    .taskId(task.getId())
                    .status(ReviewTaskStatus.PENDING.name())
                    .cached(false)
                    .build();
        } catch (RuntimeException e) {
            log.error("[Task] 任务创建失败: {}", e.getMessage());
            throw e;
        }
    }

    private ReviewTask findCachedTask(ParsedPrUrl parsedPrUrl, GitHubPrInfo prInfo) {
        return reviewTaskMapper.findLatestSuccessTaskForCache(
                parsedPrUrl.owner(),
                parsedPrUrl.repo(),
                parsedPrUrl.pullNumber(),
                prInfo.getHeadSha(),
                aiProperties.getModelName(),
                aiProperties.getPromptVersion()
        );
    }

    public ReviewTaskDetailVO getTask(Long taskId) {
        return toTaskDetailVO(requireTask(taskId));
    }

    public List<ReviewFileVO> listFiles(Long taskId) {
        requireTask(taskId);

        return reviewFileMapper.findByTaskId(taskId)
                .stream()
                .map(this::toFileVO)
                .toList();
    }

    public List<ReviewCommentVO> listComments(Long taskId, String riskLevel, String riskType) {
        requireTask(taskId);

        LambdaQueryWrapper<ReviewComment> wrapper = new LambdaQueryWrapper<ReviewComment>()
                .eq(ReviewComment::getTaskId, taskId);

        if (riskLevel != null && !riskLevel.isBlank()) {
            wrapper.eq(ReviewComment::getRiskLevel, riskLevel);
        }

        if (riskType != null && !riskType.isBlank()) {
            wrapper.eq(ReviewComment::getRiskType, riskType);
        }

        wrapper.orderByAsc(ReviewComment::getId);

        return reviewCommentMapper.selectList(wrapper)
                .stream()
                .map(this::toCommentVO)
                .toList();
    }

    public ReviewTaskPageVO listTasks(int page, int pageSize, String status, String riskLevel,
                                       String keyword, LocalDate createdFrom, LocalDate createdTo) {
        LambdaQueryWrapper<ReviewTask> wrapper = new LambdaQueryWrapper<>();

        if (status != null && !status.isBlank()) {
            wrapper.eq(ReviewTask::getStatus, status);
        }

        if (riskLevel != null && !riskLevel.isBlank()) {
            wrapper.eq(ReviewTask::getRiskLevel, riskLevel);
        }

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ReviewTask::getPrTitle, keyword)
                    .or()
                    .like(ReviewTask::getOwnerName, keyword)
                    .or()
                    .like(ReviewTask::getRepoName, keyword)
                    .or()
                    .like(ReviewTask::getPrAuthor, keyword));
        }

        if (createdFrom != null) {
            wrapper.ge(ReviewTask::getCreatedAt, createdFrom.atStartOfDay());
        }

        if (createdTo != null) {
            wrapper.le(ReviewTask::getCreatedAt, createdTo.atTime(LocalTime.MAX));
        }

        wrapper.orderByDesc(ReviewTask::getCreatedAt);

        // MyBatis-Plus pagination
        long total = reviewTaskMapper.selectCount(wrapper);
        int from = (page - 1) * pageSize;
        wrapper.last("LIMIT " + from + ", " + pageSize);

        List<ReviewTask> tasks = reviewTaskMapper.selectList(wrapper);

        List<ReviewTaskListVO> records = tasks.stream()
                .map(this::toListVO)
                .toList();

        int pages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;

        return ReviewTaskPageVO.builder()
                .records(records)
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .pages(pages)
                .build();
    }

    private ReviewTaskListVO toListVO(ReviewTask task) {
        return ReviewTaskListVO.builder()
                .taskId(task.getId())
                .prUrl(task.getPrUrl())
                .prTitle(task.getPrTitle())
                .author(task.getPrAuthor())
                .ownerName(task.getOwnerName())
                .repoName(task.getRepoName())
                .pullNumber(task.getPrNumber())
                .sourceBranch(task.getSourceBranch())
                .targetBranch(task.getTargetBranch())
                .status(task.getStatus())
                .riskScore(task.getRiskScore())
                .riskLevel(task.getRiskLevel())
                .modelName(task.getModelName())
                .promptVersion(task.getPromptVersion())
                .cached(task.getCachedFromTaskId() != null)
                .cachedFromTaskId(task.getCachedFromTaskId())
                .errorMessage(task.getErrorMessage())
                .createdAt(formatTime(task.getCreatedAt()))
                .updatedAt(formatTime(task.getUpdatedAt()))
                .build();
    }

    public ReviewTask requireTask(Long taskId) {
        ReviewTask task = reviewTaskMapper.selectById(taskId);

        if (task == null) {
            throw new BusinessException(ErrorCode.REVIEW_TASK_NOT_FOUND);
        }

        return task;
    }

    private ReviewTask createPendingTask(String prUrl, ParsedPrUrl parsedPrUrl, GitHubPrInfo prInfo) {
        ReviewTask task = new ReviewTask();
        task.setPrUrl(prUrl);
        task.setOwnerName(parsedPrUrl.owner());
        task.setRepoName(parsedPrUrl.repo());
        task.setPrNumber(parsedPrUrl.pullNumber());
        task.setPrTitle(prInfo.getTitle());
        task.setPrDescription(prInfo.getDescription());
        task.setPrAuthor(prInfo.getAuthor());
        task.setSourceBranch(prInfo.getSourceBranch());
        task.setTargetBranch(prInfo.getTargetBranch());
        task.setHeadSha(prInfo.getHeadSha());
        task.setBaseSha(prInfo.getBaseSha());
        task.setModelName(aiProperties.getModelName());
        task.setPromptVersion(aiProperties.getPromptVersion());
        task.setStatus(ReviewTaskStatus.PENDING.name());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        // 获取 commit 摘要，GitHub API 异常时设为空字符串
        String commitSummary = fetchCommitSummary(parsedPrUrl);
        task.setCommitSummary(commitSummary);

        reviewTaskMapper.insert(task);

        return task;
    }

    /**
     * 获取 PR commit 摘要，失败时返回空字符串。
     */
    private String fetchCommitSummary(ParsedPrUrl parsedPrUrl) {
        try {
            List<GitHubCommitInfo> commits = gitHubClient.getPullRequestCommits(parsedPrUrl);
            if (commits == null || commits.isEmpty()) {
                return "";
            }
            return commits.stream()
                    .map(GitHubCommitInfo::getMessage)
                    .filter(m -> m != null && !m.isBlank())
                    .limit(10)
                    .collect(java.util.stream.Collectors.joining("; "));
        } catch (Exception e) {
            log.warn("[Task] 获取 commit 摘要失败，使用空摘要: {}", e.getMessage());
            return "";
        }
    }

    private ReviewTask createCacheHitTask(ReviewTask cachedTask, ParsedPrUrl parsedPrUrl, GitHubPrInfo prInfo) {
        ReviewTask task = new ReviewTask();
        task.setPrUrl(cachedTask.getPrUrl());
        task.setOwnerName(parsedPrUrl.owner());
        task.setRepoName(parsedPrUrl.repo());
        task.setPrNumber(parsedPrUrl.pullNumber());
        task.setPrTitle(cachedTask.getPrTitle()); // 使用缓存任务的 PR 标题
        task.setPrDescription(cachedTask.getPrDescription() != null ? cachedTask.getPrDescription() : prInfo.getDescription());
        task.setPrAuthor(cachedTask.getPrAuthor());
        task.setSourceBranch(prInfo.getSourceBranch());
        task.setTargetBranch(prInfo.getTargetBranch());
        task.setHeadSha(prInfo.getHeadSha());
        task.setBaseSha(prInfo.getBaseSha());
        task.setModelName(aiProperties.getModelName());
        task.setPromptVersion(aiProperties.getPromptVersion());
        task.setStatus(ReviewTaskStatus.SUCCESS.name()); // 缓存命中直接标记为成功
        task.setCachedFromTaskId(cachedTask.getId()); // 指向原始缓存任务
        // 复制报告摘要字段
        task.setRiskScore(cachedTask.getRiskScore());
        task.setRiskLevel(cachedTask.getRiskLevel());
        task.setSummary(cachedTask.getSummary());
        task.setFinalReview(cachedTask.getFinalReview());
        task.setResultJson(cachedTask.getResultJson());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        reviewTaskMapper.insert(task);
        copyCacheHitDetails(cachedTask.getId(), task.getId());

        log.info("[Task] 缓存命中记录已创建, newTaskId={}, cachedFromTaskId={}", task.getId(), cachedTask.getId());
        return task;
    }

    private void copyCacheHitDetails(Long sourceTaskId, Long targetTaskId) {
        List<ReviewFile> sourceFiles = reviewFileMapper.findByTaskId(sourceTaskId);
        if (!sourceFiles.isEmpty()) {
            List<ReviewFile> copiedFiles = sourceFiles.stream()
                    .map(file -> copyFileForTask(file, targetTaskId))
                    .toList();
            reviewFileMapper.insertBatch(copiedFiles);
        }

        List<ReviewComment> sourceComments = reviewCommentMapper.findByTaskId(sourceTaskId);
        if (!sourceComments.isEmpty()) {
            List<ReviewComment> copiedComments = sourceComments.stream()
                    .map(comment -> copyCommentForTask(comment, targetTaskId))
                    .toList();
            reviewCommentMapper.insertBatch(copiedComments);
        }

        log.info("[Task] 缓存命中详情复制完成, sourceTaskId={}, targetTaskId={}, fileCount={}, commentCount={}",
                sourceTaskId, targetTaskId, sourceFiles.size(), sourceComments.size());
    }

    private ReviewFile copyFileForTask(ReviewFile source, Long targetTaskId) {
        ReviewFile target = new ReviewFile();
        target.setTaskId(targetTaskId);
        target.setFilePath(source.getFilePath());
        target.setFileStatus(source.getFileStatus());
        target.setLanguage(source.getLanguage());
        target.setAdditions(source.getAdditions());
        target.setDeletions(source.getDeletions());
        target.setChanges(source.getChanges());
        target.setPatch(source.getPatch());
        target.setOriginalPatchLength(source.getOriginalPatchLength());
        target.setAnalyzedPatchLength(source.getAnalyzedPatchLength());
        target.setTruncated(source.getTruncated());
        target.setAiSummary(source.getAiSummary());
        target.setSkipped(source.getSkipped());
        target.setSkipReason(source.getSkipReason());
        return target;
    }

    private ReviewComment copyCommentForTask(ReviewComment source, Long targetTaskId) {
        ReviewComment target = new ReviewComment();
        target.setTaskId(targetTaskId);
        target.setFilePath(source.getFilePath());
        target.setLineNumber(source.getLineNumber());
        target.setRiskType(source.getRiskType());
        target.setRiskLevel(source.getRiskLevel());
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setReason(source.getReason());
        target.setEvidence(source.getEvidence());
        target.setActionLevel(source.getActionLevel());
        target.setSuggestion(source.getSuggestion());
        target.setConfidence(source.getConfidence());
        target.setNeedHumanCheck(source.getNeedHumanCheck());
        return target;
    }

    private void saveChangedFiles(Long taskId, List<GitHubChangedFile> changedFiles) {
        if (changedFiles == null || changedFiles.isEmpty()) {
            return;
        }

        List<ReviewFile> files = diffPreprocessor.preprocess(changedFiles).stream()
                .map(file -> toReviewFile(taskId, file))
                .toList();

        reviewFileMapper.insertBatch(files);
    }

    private ReviewFile toReviewFile(Long taskId, DiffPreprocessor.PreparedFile preparedFile) {
        GitHubChangedFile changedFile = preparedFile.file();

        ReviewFile file = new ReviewFile();
        file.setTaskId(taskId);
        file.setFilePath(changedFile.getFilename());
        file.setFileStatus(changedFile.getStatus());
        file.setLanguage(promptRenderer.detectLanguage(changedFile.getFilename()));
        file.setAdditions(defaultInt(changedFile.getAdditions()));
        file.setDeletions(defaultInt(changedFile.getDeletions()));
        file.setChanges(defaultInt(changedFile.getChanges()));
        file.setPatch(preparedFile.analyzedPatch());
        file.setOriginalPatchLength(preparedFile.originalPatchLength());
        file.setAnalyzedPatchLength(preparedFile.analyzedPatchLength());
        file.setTruncated(preparedFile.truncated());
        file.setSkipped(preparedFile.skipped());
        file.setSkipReason(preparedFile.skipReason());
        file.setCreatedAt(LocalDateTime.now());

        return file;
    }

    private void updateStatus(Long taskId, ReviewTaskStatus status, String errorMessage) {
        ReviewTask update = new ReviewTask();
        update.setId(taskId);
        update.setStatus(status.name());
        update.setErrorMessage(errorMessage);
        update.setUpdatedAt(LocalDateTime.now());

        reviewTaskMapper.updateById(update);
    }

    // ── P0：状态 → 中文阶段描述 ──
    private static final String STAGE_PENDING = "创建任务";
    private static final String STAGE_FETCHING_PR = "获取 PR 信息";
    private static final String STAGE_PARSING_DIFF = "解析 Diff";
    private static final String STAGE_REVIEWING = "执行 AI Review";
    private static final String STAGE_SUMMARIZING = "生成报告";
    private static final String STAGE_SCORING = "计算风险评分";
    private static final String STAGE_SUCCESS = "完成";
    private static final String STAGE_PARTIAL_SUCCESS = "部分完成";
    private static final String STAGE_FAILED = "失败";
    private static final String STAGE_CANCELLED = "已取消";
    private static final String STAGE_UNKNOWN = "未知";

    private String toCurrentStep(String status) {
        if (status == null) return STAGE_UNKNOWN;
        return switch (status.toUpperCase()) {
            case "PENDING" -> STAGE_PENDING;
            case "FETCHING_PR" -> STAGE_FETCHING_PR;
            case "PARSING_DIFF" -> STAGE_PARSING_DIFF;
            case "REVIEWING" -> STAGE_REVIEWING;
            case "SUMMARIZING" -> STAGE_SUMMARIZING;
            case "SCORING" -> STAGE_SCORING;
            case "SUCCESS" -> STAGE_SUCCESS;
            case "PARTIAL_SUCCESS" -> STAGE_PARTIAL_SUCCESS;
            case "FAILED" -> STAGE_FAILED;
            case "CANCELLED" -> STAGE_CANCELLED;
            default -> STAGE_UNKNOWN;
        };
    }

    private boolean isTerminal(String status) {
        if (status == null) return false;
        return switch (status.toUpperCase()) {
            case "SUCCESS", "PARTIAL_SUCCESS", "FAILED", "CANCELLED" -> true;
            default -> false;
        };
    }

    private ReviewTaskDetailVO toTaskDetailVO(ReviewTask task) {
        // 统计文件数量，计算进度
        java.util.List<ReviewFile> allFiles = reviewFileMapper.findByTaskId(task.getId());
        int total = allFiles.size();
        long skipped = allFiles.stream().filter(f -> Boolean.TRUE.equals(f.getSkipped())).count();
        long analyzed = allFiles.stream()
                .filter(f -> !Boolean.TRUE.equals(f.getSkipped()))
                .filter(f -> f.getAiSummary() != null && !f.getAiSummary().isBlank())
                .filter(f -> !f.getAiSummary().startsWith("[分析失败]"))
                .count();
        long failed = allFiles.stream()
                .filter(f -> !Boolean.TRUE.equals(f.getSkipped()))
                .filter(f -> f.getAiSummary() != null && f.getAiSummary().startsWith("[分析失败]"))
                .count();
        int active = (int) (total - skipped);
        int pct = active > 0
                ? Math.min(100, (int) (analyzed * 100 / active))
                : (isTerminal(task.getStatus()) ? 100 : 0);

        return ReviewTaskDetailVO.builder()
                .taskId(task.getId())
                .prUrl(task.getPrUrl())
                .prTitle(task.getPrTitle())
                .author(task.getPrAuthor())
                .sourceBranch(task.getSourceBranch())
                .targetBranch(task.getTargetBranch())
                .status(task.getStatus())
                .riskScore(task.getRiskScore())
                .riskLevel(task.getRiskLevel())
                .errorMessage(task.getErrorMessage())
                .createdAt(formatTime(task.getCreatedAt()))
                .updatedAt(formatTime(task.getUpdatedAt()))
                // ── P0 进度 ──
                .progressPercent(pct)
                .currentStep(toCurrentStep(task.getStatus()))
                .totalFileCount(total)
                .analyzedFileCount((int) analyzed)
                .skippedFileCount((int) skipped)
                .failedFileCount((int) failed)
                // ── 缓存/模型 ──
                .cached(task.getCachedFromTaskId() != null)
                .cachedFromTaskId(task.getCachedFromTaskId())
                .modelName(task.getModelName())
                .promptVersion(task.getPromptVersion())
                .build();
    }

    private ReviewFileVO toFileVO(ReviewFile file) {
        return ReviewFileVO.builder()
                .fileId(file.getId())
                .taskId(file.getTaskId())
                .filePath(file.getFilePath())
                .fileStatus(file.getFileStatus())
                .language(file.getLanguage())
                .additions(file.getAdditions())
                .deletions(file.getDeletions())
                .changes(file.getChanges())
                .aiSummary(file.getAiSummary())
                .originalPatchLength(file.getOriginalPatchLength())
                .analyzedPatchLength(file.getAnalyzedPatchLength())
                .truncated(file.getTruncated())
                .skipped(file.getSkipped())
                .skipReason(file.getSkipReason())
                .build();
    }

    private ReviewCommentVO toCommentVO(ReviewComment comment) {
        BigDecimal confidence = comment.getConfidence();

        return ReviewCommentVO.builder()
                .id(comment.getId())
                .taskId(comment.getTaskId())
                .filePath(comment.getFilePath())
                .line(comment.getLineNumber())
                .riskType(comment.getRiskType())
                .riskLevel(comment.getRiskLevel())
                .title(comment.getTitle())
                .description(comment.getDescription())
                .reason(comment.getReason())
                .evidence(comment.getEvidence())
                .actionLevel(comment.getActionLevel())
                .suggestion(comment.getSuggestion())
                .confidence(confidence == null ? null : confidence.doubleValue())
                .needHumanCheck(comment.getNeedHumanCheck())
                .build();
    }

    private String formatTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATE_TIME_FORMATTER);
    }

    private Integer defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
