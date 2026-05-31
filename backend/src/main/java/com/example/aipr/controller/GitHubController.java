package com.example.aipr.controller;

import com.example.aipr.common.Result;
import com.example.aipr.dto.PrUrlRequest;
import com.example.aipr.service.github.GitHubPullRequestService;
import com.example.aipr.vo.GitHubPullRequestPageVO;
import com.example.aipr.vo.GitHubPullRequestReviewStateVO;
import com.example.aipr.vo.GitHubPrPreviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/github")
public class GitHubController {

    private final GitHubPullRequestService gitHubPullRequestService;

    @PostMapping("/preview")
    public Result<GitHubPrPreviewVO> preview(@Valid @RequestBody PrUrlRequest request) {
        return Result.ok(gitHubPullRequestService.preview(request.getPrUrl()));
    }

    @GetMapping("/pulls")
    public Result<GitHubPullRequestPageVO> listPullRequests(
            @RequestParam String owner,
            @RequestParam String repo,
            @RequestParam(defaultValue = "open") String state,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(gitHubPullRequestService.listPullRequests(owner, repo, state, page, pageSize));
    }

    @GetMapping("/pulls/{owner}/{repo}/{pullNumber}/review-state")
    public Result<GitHubPullRequestReviewStateVO> getReviewState(@PathVariable String owner,
                                                                 @PathVariable String repo,
                                                                 @PathVariable Integer pullNumber) {
        return Result.ok(gitHubPullRequestService.getReviewState(owner, repo, pullNumber));
    }
}
