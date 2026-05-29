package com.aipr.review.service.github;

import com.aipr.review.common.BusinessException;
import com.aipr.review.enums.ErrorCode;
import com.aipr.review.vo.ChangedFileVO;
import com.aipr.review.vo.GitHubPrPreviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GitHubPullRequestService {

    private final PrUrlParser prUrlParser;
    private final GitHubClient gitHubClient;

    public GitHubPrPreviewVO preview(String prUrl) {
        ParsedPrUrl parsedPrUrl = prUrlParser.parse(prUrl);
        GitHubPullRequest pullRequest = fetch(parsedPrUrl, prUrl);

        return GitHubPrPreviewVO.builder()
                .owner(pullRequest.getOwner())
                .repo(pullRequest.getRepo())
                .pullNumber(pullRequest.getPullNumber())
                .title(pullRequest.getTitle())
                .body(pullRequest.getBody())
                .author(pullRequest.getAuthor())
                .sourceBranch(pullRequest.getSourceBranch())
                .targetBranch(pullRequest.getTargetBranch())
                .state(pullRequest.getState())
                .additions(pullRequest.getAdditions())
                .deletions(pullRequest.getDeletions())
                .changedFiles(pullRequest.getChangedFiles())
                .files(pullRequest.getFiles().stream()
                        .map(this::toChangedFileVO)
                        .toList())
                .build();
    }

    public GitHubPullRequest fetch(String prUrl) {
        ParsedPrUrl parsedPrUrl = prUrlParser.parse(prUrl);
        return fetch(parsedPrUrl, prUrl);
    }

    public GitHubPullRequest fetch(ParsedPrUrl parsedPrUrl, String prUrl) {
        GitHubPullRequestResponse pullRequest = gitHubClient.getPullRequest(parsedPrUrl);
        List<GitHubChangedFileResponse> changedFiles = gitHubClient.listChangedFiles(parsedPrUrl);

        if (pullRequest == null) {
            throw new BusinessException(ErrorCode.GITHUB_API_ERROR);
        }

        List<GitHubChangedFile> files = Objects.requireNonNullElse(changedFiles, List.<GitHubChangedFileResponse>of())
                .stream()
                .map(this::toChangedFile)
                .toList();

        return GitHubPullRequest.builder()
                .owner(parsedPrUrl.owner())
                .repo(parsedPrUrl.repo())
                .pullNumber(parsedPrUrl.pullNumber())
                .prUrl(prUrl)
                .title(pullRequest.getTitle())
                .body(pullRequest.getBody())
                .author(pullRequest.getUser() == null ? null : pullRequest.getUser().getLogin())
                .sourceBranch(pullRequest.getHead() == null ? null : pullRequest.getHead().getRef())
                .targetBranch(pullRequest.getBase() == null ? null : pullRequest.getBase().getRef())
                .state(pullRequest.getState())
                .additions(pullRequest.getAdditions())
                .deletions(pullRequest.getDeletions())
                .changedFiles(pullRequest.getChangedFiles())
                .files(files)
                .build();
    }

    private GitHubChangedFile toChangedFile(GitHubChangedFileResponse file) {
        return GitHubChangedFile.builder()
                .filename(file.getFilename())
                .status(file.getStatus())
                .additions(file.getAdditions())
                .deletions(file.getDeletions())
                .changes(file.getChanges())
                .patch(file.getPatch())
                .build();
    }

    private ChangedFileVO toChangedFileVO(GitHubChangedFile file) {
        return ChangedFileVO.builder()
                .filename(file.getFilename())
                .status(file.getStatus())
                .additions(file.getAdditions())
                .deletions(file.getDeletions())
                .changes(file.getChanges())
                .patch(file.getPatch())
                .build();
    }
}
