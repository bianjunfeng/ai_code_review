package com.example.aipr.service.github;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aipr.common.BusinessException;
import com.example.aipr.config.AiProperties;
import com.example.aipr.entity.ReviewTask;
import com.example.aipr.enums.ErrorCode;
import com.example.aipr.mapper.ReviewTaskMapper;
import com.example.aipr.vo.ChangedFileVO;
import com.example.aipr.vo.GitHubPullRequestPageVO;
import com.example.aipr.vo.GitHubPullRequestReviewStateVO;
import com.example.aipr.vo.GitHubPullRequestVO;
import com.example.aipr.vo.GitHubPrPreviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GitHubPullRequestService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PrUrlParser prUrlParser;
    private final GitHubClient gitHubClient;
    private final ReviewTaskMapper reviewTaskMapper;
    private final AiProperties aiProperties;

    public GitHubPrPreviewVO preview(String prUrl) {
        ParsedPrUrl parsedPrUrl = prUrlParser.parse(prUrl);
        GitHubPrInfo prInfo = gitHubClient.getPullRequest(parsedPrUrl);
        List<GitHubChangedFile> changedFiles = gitHubClient.getPullRequestFiles(parsedPrUrl);

        return GitHubPrPreviewVO.builder()
                .owner(prInfo.getOwner())
                .repo(prInfo.getRepo())
                .pullNumber(prInfo.getPullNumber())
                .title(prInfo.getTitle())
                .author(prInfo.getAuthor())
                .sourceBranch(prInfo.getSourceBranch())
                .targetBranch(prInfo.getTargetBranch())
                .state(prInfo.getState())
                .additions(prInfo.getAdditions())
                .deletions(prInfo.getDeletions())
                .changedFiles(changedFiles.size())
                .files(changedFiles.stream()
                        .map(file -> ChangedFileVO.builder()
                                .filename(file.getFilename())
                                .status(file.getStatus())
                                .additions(file.getAdditions())
                                .deletions(file.getDeletions())
                                .changes(file.getChanges())
                                .patch(file.getPatch())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    public GitHubPullRequestPageVO listPullRequests(String owner, String repo, String state, int page, int pageSize) {
        String normalizedOwner = requireText(owner, "仓库 owner 不能为空");
        String normalizedRepo = requireText(repo, "仓库名称不能为空");
        String normalizedState = normalizeState(state);
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));

        List<GitHubPrInfo> pullRequests = gitHubClient.listPullRequests(
                normalizedOwner,
                normalizedRepo,
                normalizedState,
                safePage,
                safePageSize
        );

        Map<Integer, ReviewTask> latestTaskByPr = findLatestTasks(
                normalizedOwner,
                normalizedRepo,
                pullRequests.stream()
                        .map(GitHubPrInfo::getPullNumber)
                        .toList()
        );

        List<GitHubPullRequestVO> records = pullRequests.stream()
                .map(pr -> toPullRequestVO(pr, latestTaskByPr.get(pr.getPullNumber())))
                .toList();

        return GitHubPullRequestPageVO.builder()
                .records(records)
                .page(safePage)
                .pageSize(safePageSize)
                .total(null)
                .pages(null)
                .build();
    }

    public GitHubPullRequestReviewStateVO getReviewState(String owner, String repo, Integer pullNumber) {
        String normalizedOwner = requireText(owner, "仓库 owner 不能为空");
        String normalizedRepo = requireText(repo, "仓库名称不能为空");
        if (pullNumber == null || pullNumber <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "PR 编号必须大于 0");
        }

        GitHubPrInfo prInfo = gitHubClient.getPullRequest(new ParsedPrUrl(normalizedOwner, normalizedRepo, pullNumber));
        ReviewTask latestTask = findLatestTask(normalizedOwner, normalizedRepo, pullNumber);

        return GitHubPullRequestReviewStateVO.builder()
                .owner(normalizedOwner)
                .repo(normalizedRepo)
                .pullNumber(pullNumber)
                .reviewed(latestTask != null)
                .latestTaskId(latestTask == null ? null : latestTask.getId())
                .latestTaskStatus(latestTask == null ? null : latestTask.getStatus())
                .latestRiskLevel(latestTask == null ? null : latestTask.getRiskLevel())
                .latestRiskScore(latestTask == null ? null : latestTask.getRiskScore())
                .cachedAvailable(isCacheAvailable(prInfo))
                .updatedAt(latestTask == null ? null : formatTime(latestTask.getUpdatedAt()))
                .build();
    }

    private GitHubPullRequestVO toPullRequestVO(GitHubPrInfo prInfo, ReviewTask latestTask) {
        return GitHubPullRequestVO.builder()
                .owner(prInfo.getOwner())
                .repo(prInfo.getRepo())
                .pullNumber(prInfo.getPullNumber())
                .title(prInfo.getTitle())
                .author(prInfo.getAuthor())
                .state(prInfo.getState())
                .sourceBranch(prInfo.getSourceBranch())
                .targetBranch(prInfo.getTargetBranch())
                .htmlUrl(prInfo.getHtmlUrl())
                .createdAt(prInfo.getCreatedAt())
                .updatedAt(prInfo.getUpdatedAt())
                .draft(prInfo.getDraft())
                .reviewed(latestTask != null)
                .latestTaskId(latestTask == null ? null : latestTask.getId())
                .latestTaskStatus(latestTask == null ? null : latestTask.getStatus())
                .latestRiskLevel(latestTask == null ? null : latestTask.getRiskLevel())
                .latestRiskScore(latestTask == null ? null : latestTask.getRiskScore())
                .cachedAvailable(isCacheAvailable(prInfo))
                .build();
    }

    private Map<Integer, ReviewTask> findLatestTasks(String owner, String repo, List<Integer> pullNumbers) {
        List<Integer> validPullNumbers = pullNumbers.stream()
                .filter(number -> number != null && number > 0)
                .distinct()
                .toList();
        if (validPullNumbers.isEmpty()) {
            return Map.of();
        }

        LambdaQueryWrapper<ReviewTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReviewTask::getOwnerName, owner)
                .eq(ReviewTask::getRepoName, repo)
                .in(ReviewTask::getPrNumber, validPullNumbers)
                .orderByDesc(ReviewTask::getCreatedAt);

        return reviewTaskMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(
                        ReviewTask::getPrNumber,
                        task -> task,
                        (latest, ignored) -> latest,
                        LinkedHashMap::new
                ));
    }

    private ReviewTask findLatestTask(String owner, String repo, Integer pullNumber) {
        LambdaQueryWrapper<ReviewTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReviewTask::getOwnerName, owner)
                .eq(ReviewTask::getRepoName, repo)
                .eq(ReviewTask::getPrNumber, pullNumber)
                .orderByDesc(ReviewTask::getCreatedAt)
                .last("LIMIT 1");
        return reviewTaskMapper.selectOne(wrapper);
    }

    private boolean isCacheAvailable(GitHubPrInfo prInfo) {
        if (prInfo.getHeadSha() == null || prInfo.getHeadSha().isBlank()) {
            return false;
        }
        return reviewTaskMapper.findLatestSuccessTaskForCache(
                prInfo.getOwner(),
                prInfo.getRepo(),
                prInfo.getPullNumber(),
                prInfo.getHeadSha(),
                aiProperties.getModelName(),
                aiProperties.getPromptVersion()
        ) != null;
    }

    private String normalizeState(String state) {
        String normalized = state == null || state.isBlank() ? "open" : state.trim().toLowerCase(Locale.ROOT);
        if (!List.of("open", "closed", "all").contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "PR 状态只支持 open、closed、all");
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message);
        }
        return value.trim();
    }

    private String formatTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATE_TIME_FORMATTER);
    }
}
