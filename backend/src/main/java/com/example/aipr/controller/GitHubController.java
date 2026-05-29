package com.example.aipr.controller;

import com.example.aipr.common.Result;
import com.example.aipr.dto.PrUrlRequest;
import com.example.aipr.service.github.GitHubPullRequestService;
import com.example.aipr.vo.GitHubPrPreviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
