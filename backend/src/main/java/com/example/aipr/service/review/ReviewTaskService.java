package com.example.aipr.service.review;

<<<<<<< Updated upstream
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aipr.common.BusinessException;
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
import com.example.aipr.service.github.GitHubPrInfo;
import com.example.aipr.service.github.ParsedPrUrl;
=======
import com.example.aipr.entity.ReviewTask;
import com.example.aipr.enums.ReviewTaskStatus;
import com.example.aipr.mapper.ReviewTaskMapper;
>>>>>>> Stashed changes
import com.example.aipr.service.github.PrUrlParser;
import com.example.aipr.service.prompt.PromptRenderer;
import com.example.aipr.vo.ReviewCommentVO;
import com.example.aipr.vo.ReviewFileVO;
import com.example.aipr.vo.ReviewTaskCreatedVO;
import com.example.aipr.vo.ReviewTaskDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

<<<<<<< Updated upstream
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

=======
>>>>>>> Stashed changes
@Service
@RequiredArgsConstructor
public class ReviewTaskService {

<<<<<<< Updated upstream
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PrUrlParser prUrlParser;
    private final GitHubClient gitHubClient;
    private final PromptRenderer promptRenderer;
    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewFileMapper reviewFileMapper;
    private final ReviewCommentMapper reviewCommentMapper;
    private final ReviewTaskExecutor reviewTaskExecutor;

    public ReviewTaskCreatedVO createTask(String prUrl) {
        ParsedPrUrl parsedPrUrl = prUrlParser.parse(prUrl);
        ReviewTask task = createPendingTask(prUrl, parsedPrUrl);

        try {
            updateStatus(task.getId(), ReviewTaskStatus.FETCHING_PR, null);
            GitHubPrInfo prInfo = gitHubClient.getPullRequest(parsedPrUrl);
            fillPullRequestInfo(task, prInfo);
            reviewTaskMapper.updateById(task);

            updateStatus(task.getId(), ReviewTaskStatus.PARSING_DIFF, null);
            List<GitHubChangedFile> changedFiles = gitHubClient.getPullRequestFiles(parsedPrUrl);
            saveChangedFiles(task.getId(), changedFiles);

            updateStatus(task.getId(), ReviewTaskStatus.PENDING, null);
            reviewTaskExecutor.executeAsync(task.getId());

            return ReviewTaskCreatedVO.builder()
                    .taskId(task.getId())
                    .status(ReviewTaskStatus.PENDING.name())
                    .build();
        } catch (RuntimeException e) {
            updateStatus(task.getId(), ReviewTaskStatus.FAILED, e.getMessage());
            throw e;
        }
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

    public ReviewTask requireTask(Long taskId) {
        ReviewTask task = reviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.REVIEW_TASK_NOT_FOUND);
        }
        return task;
    }

    private ReviewTask createPendingTask(String prUrl, ParsedPrUrl parsedPrUrl) {
        ReviewTask task = new ReviewTask();
        task.setPrUrl(prUrl);
        task.setOwnerName(parsedPrUrl.owner());
        task.setRepoName(parsedPrUrl.repo());
        task.setPrNumber(parsedPrUrl.pullNumber());
        task.setStatus(ReviewTaskStatus.PENDING.name());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        reviewTaskMapper.insert(task);
        return task;
    }

    private void fillPullRequestInfo(ReviewTask task, GitHubPrInfo prInfo) {
        task.setPrTitle(prInfo.getTitle());
        task.setPrAuthor(prInfo.getAuthor());
        task.setSourceBranch(prInfo.getSourceBranch());
        task.setTargetBranch(prInfo.getTargetBranch());
        task.setUpdatedAt(LocalDateTime.now());
    }

    private void saveChangedFiles(Long taskId, List<GitHubChangedFile> changedFiles) {
        if (changedFiles == null || changedFiles.isEmpty()) {
            return;
        }

        List<ReviewFile> files = changedFiles.stream()
                .map(file -> toReviewFile(taskId, file))
                .toList();
        reviewFileMapper.insertBatch(files);
    }

    private ReviewFile toReviewFile(Long taskId, GitHubChangedFile changedFile) {
        ReviewFile file = new ReviewFile();
        file.setTaskId(taskId);
        file.setFilePath(changedFile.getFilename());
        file.setFileStatus(changedFile.getStatus());
        file.setLanguage(promptRenderer.detectLanguage(changedFile.getFilename()));
        file.setAdditions(defaultInt(changedFile.getAdditions()));
        file.setDeletions(defaultInt(changedFile.getDeletions()));
        file.setChanges(defaultInt(changedFile.getChanges()));
        file.setPatch(changedFile.getPatch());
        file.setSkipped(false);
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

    private ReviewTaskDetailVO toTaskDetailVO(ReviewTask task) {
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
=======
    private final ReviewTaskMapper reviewTaskMapper;
    private final PrUrlParser prUrlParser;

    public ReviewTaskCreatedVO createTask(String prUrl) {
        var parsed = prUrlParser.parse(prUrl);

        ReviewTask task = new ReviewTask();
        task.setPrUrl(prUrl);
        task.setOwnerName(parsed.owner());
        task.setRepoName(parsed.repo());
        task.setPrNumber(parsed.pullNumber());
        task.setStatus(ReviewTaskStatus.PENDING.name());

        reviewTaskMapper.insert(task);

        return ReviewTaskCreatedVO.builder()
                .taskId(task.getId())
                .status(task.getStatus())
>>>>>>> Stashed changes
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
