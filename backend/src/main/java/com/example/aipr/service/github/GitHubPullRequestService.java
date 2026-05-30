package com.example.aipr.service.github;

import com.example.aipr.vo.ChangedFileVO;
import com.example.aipr.vo.GitHubPrPreviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GitHubPullRequestService {

    private final PrUrlParser prUrlParser;
    private final GitHubClient gitHubClient;

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
}
