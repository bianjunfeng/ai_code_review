package com.example.aipr.service.github;

import com.example.aipr.vo.GitHubPrPreviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GitHubPullRequestService {

    private final PrUrlParser prUrlParser;

    public GitHubPrPreviewVO preview(String prUrl) {
        ParsedPrUrl parsedPrUrl = prUrlParser.parse(prUrl);

        return GitHubPrPreviewVO.builder()
                .owner(parsedPrUrl.owner())
                .repo(parsedPrUrl.repo())
                .pullNumber(parsedPrUrl.pullNumber())
                .title("待接入 GitHub API 的 PR 预览")
                .author("unknown")
                .sourceBranch("head")
                .targetBranch("base")
                .state("OPEN")
                .additions(0)
                .deletions(0)
                .changedFiles(0)
                .files(List.of())
                .build();
    }
}
