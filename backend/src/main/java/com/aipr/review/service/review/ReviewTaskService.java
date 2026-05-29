package com.aipr.review.service.review;

import com.aipr.review.common.BusinessException;
import com.aipr.review.domain.ReviewComment;
import com.aipr.review.domain.ReviewFile;
import com.aipr.review.domain.ReviewTask;
import com.aipr.review.enums.ErrorCode;
import com.aipr.review.enums.ReviewTaskStatus;
import com.aipr.review.service.github.GitHubChangedFile;
import com.aipr.review.service.github.GitHubPullRequest;
import com.aipr.review.service.github.GitHubPullRequestService;
import com.aipr.review.service.github.ParsedPrUrl;
import com.aipr.review.service.github.PrUrlParser;
import com.aipr.review.vo.ReviewCommentVO;
import com.aipr.review.vo.ReviewFileVO;
import com.aipr.review.vo.ReviewTaskDetailVO;
import com.aipr.review.vo.ReviewTaskCreatedVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReviewTaskService {

    private final PrUrlParser prUrlParser;
    private final GitHubPullRequestService gitHubPullRequestService;
    private final ReviewTaskStore reviewTaskStore;

    public ReviewTaskCreatedVO createTask(String prUrl) {
        ParsedPrUrl parsedPrUrl = prUrlParser.parse(prUrl);
        ReviewTask task = ReviewTask.builder()
                .prUrl(prUrl)
                .owner(parsedPrUrl.owner())
                .repo(parsedPrUrl.repo())
                .pullNumber(parsedPrUrl.pullNumber())
                .status(ReviewTaskStatus.PENDING)
                .highCount(0)
                .mediumCount(0)
                .lowCount(0)
                .infoCount(0)
                .startedAt(LocalDateTime.now())
                .build();
        reviewTaskStore.saveTask(task);

        try {
            updateStatus(task, ReviewTaskStatus.FETCHING_PR);
            GitHubPullRequest pullRequest = gitHubPullRequestService.fetch(parsedPrUrl, prUrl);
            fillPullRequestInfo(task, pullRequest);

            updateStatus(task, ReviewTaskStatus.PARSING_DIFF);
            List<ReviewFile> reviewFiles = pullRequest.getFiles().stream()
                    .map(file -> toReviewFile(task.getId(), file))
                    .toList();
            reviewTaskStore.saveFiles(task.getId(), reviewFiles);

            task.setSummary(buildPendingSummary(task, reviewFiles));
            updateStatus(task, ReviewTaskStatus.REVIEWING);
        } catch (RuntimeException ex) {
            task.setStatus(ReviewTaskStatus.FAILED);
            task.setErrorMessage(ex.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            reviewTaskStore.saveTask(task);
            throw ex;
        }

        return ReviewTaskCreatedVO.builder()
                .taskId(task.getId())
                .status(task.getStatus().name())
                .build();
    }

    public ReviewTaskDetailVO getTask(Long taskId) {
        return toTaskDetailVO(findTaskOrThrow(taskId));
    }

    public List<ReviewFileVO> getFiles(Long taskId) {
        findTaskOrThrow(taskId);
        return reviewTaskStore.findFiles(taskId).stream()
                .map(this::toReviewFileVO)
                .toList();
    }

    public List<ReviewCommentVO> getComments(Long taskId) {
        findTaskOrThrow(taskId);
        return reviewTaskStore.findComments(taskId).stream()
                .map(this::toReviewCommentVO)
                .toList();
    }

    public ReviewTask findTaskOrThrow(Long taskId) {
        return reviewTaskStore.findTask(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_TASK_NOT_FOUND));
    }

    private void fillPullRequestInfo(ReviewTask task, GitHubPullRequest pullRequest) {
        task.setPrTitle(pullRequest.getTitle());
        task.setPrDescription(pullRequest.getBody());
        task.setPrAuthor(pullRequest.getAuthor());
        task.setSourceBranch(pullRequest.getSourceBranch());
        task.setTargetBranch(pullRequest.getTargetBranch());
        task.setPrState(pullRequest.getState());
        task.setAdditions(nullToZero(pullRequest.getAdditions()));
        task.setDeletions(nullToZero(pullRequest.getDeletions()));
        task.setChangedFiles(nullToZero(pullRequest.getChangedFiles()));
        reviewTaskStore.saveTask(task);
    }

    private ReviewFile toReviewFile(Long taskId, GitHubChangedFile file) {
        String filename = Objects.toString(file.getFilename(), "");
        SkipDecision skipDecision = decideSkip(filename, file.getPatch());

        return ReviewFile.builder()
                .taskId(taskId)
                .filePath(filename)
                .fileStatus(file.getStatus())
                .language(detectLanguage(filename))
                .additions(nullToZero(file.getAdditions()))
                .deletions(nullToZero(file.getDeletions()))
                .changes(nullToZero(file.getChanges()))
                .patch(file.getPatch())
                .skipped(skipDecision.skipped())
                .skipReason(skipDecision.reason())
                .build();
    }

    private void updateStatus(ReviewTask task, ReviewTaskStatus status) {
        task.setStatus(status);
        reviewTaskStore.saveTask(task);
    }

    private String buildPendingSummary(ReviewTask task, List<ReviewFile> files) {
        long reviewableCount = files.stream()
                .filter(file -> !Boolean.TRUE.equals(file.getSkipped()))
                .count();
        return "已获取 PR 信息和 " + files.size() + " 个变更文件，其中 " + reviewableCount + " 个文件等待 AI Review。";
    }

    private SkipDecision decideSkip(String filename, String patch) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".svg") || lower.endsWith(".ico")
                || lower.endsWith(".zip") || lower.endsWith(".jar") || lower.endsWith(".class")
                || lower.endsWith(".min.js")) {
            return new SkipDecision(true, "静态资源、压缩包或构建产物默认跳过");
        }
        if (lower.endsWith("package-lock.json") || lower.endsWith("yarn.lock") || lower.endsWith("pnpm-lock.yaml")) {
            return new SkipDecision(true, "依赖锁文件默认跳过");
        }
        if (lower.startsWith("dist/") || lower.startsWith("target/") || lower.startsWith("node_modules/")) {
            return new SkipDecision(true, "构建目录或依赖目录默认跳过");
        }
        if (patch == null || patch.isBlank()) {
            return new SkipDecision(true, "GitHub 未返回 patch，暂不进入 AI Review");
        }
        return new SkipDecision(false, null);
    }

    private String detectLanguage(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) {
            return "Java";
        }
        if (lower.endsWith(".xml")) {
            return "XML";
        }
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            return "YAML";
        }
        if (lower.endsWith(".sql")) {
            return "SQL";
        }
        if (lower.endsWith(".js")) {
            return "JavaScript";
        }
        if (lower.endsWith(".ts")) {
            return "TypeScript";
        }
        if (lower.endsWith(".vue")) {
            return "Vue";
        }
        if (lower.endsWith(".py")) {
            return "Python";
        }
        if (lower.endsWith(".md")) {
            return "Markdown";
        }
        if (lower.endsWith(".json")) {
            return "JSON";
        }
        return "Unknown";
    }

    private ReviewTaskDetailVO toTaskDetailVO(ReviewTask task) {
        return ReviewTaskDetailVO.builder()
                .taskId(task.getId())
                .prUrl(task.getPrUrl())
                .owner(task.getOwner())
                .repo(task.getRepo())
                .pullNumber(task.getPullNumber())
                .prTitle(task.getPrTitle())
                .prAuthor(task.getPrAuthor())
                .sourceBranch(task.getSourceBranch())
                .targetBranch(task.getTargetBranch())
                .status(task.getStatus().name())
                .additions(task.getAdditions())
                .deletions(task.getDeletions())
                .changedFiles(task.getChangedFiles())
                .summary(task.getSummary())
                .errorMessage(task.getErrorMessage())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
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

    private ReviewCommentVO toReviewCommentVO(ReviewComment comment) {
        return ReviewCommentVO.builder()
                .filePath(comment.getFilePath())
                .lineNumber(comment.getLineNumber())
                .riskType(comment.getRiskType())
                .severity(comment.getSeverity())
                .title(comment.getTitle())
                .description(comment.getDescription())
                .suggestion(comment.getSuggestion())
                .confidence(comment.getConfidence())
                .needHumanCheck(comment.getNeedHumanCheck())
                .build();
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private record SkipDecision(boolean skipped, String reason) {
    }
}
