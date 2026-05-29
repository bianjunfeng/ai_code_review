package com.aipr.review.service.github;

import com.aipr.review.common.BusinessException;
import com.aipr.review.config.GitHubProperties;
import com.aipr.review.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Component
public class GitHubClient {

    private final GitHubProperties gitHubProperties;
    private final RestClient restClient;

    public GitHubClient(GitHubProperties gitHubProperties, RestClient.Builder restClientBuilder) {
        this.gitHubProperties = gitHubProperties;
        this.restClient = restClientBuilder
                .baseUrl(gitHubProperties.getApiBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public GitHubPullRequestResponse getPullRequest(ParsedPrUrl parsedPrUrl) {
        try {
            log.info("Fetching GitHub PR info: {}/{}#{}",
                    parsedPrUrl.owner(), parsedPrUrl.repo(), parsedPrUrl.pullNumber());

            return restClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{pullNumber}",
                            parsedPrUrl.owner(), parsedPrUrl.repo(), parsedPrUrl.pullNumber())
                    .headers(this::applyAuthHeader)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw toBusinessException(response.getStatusCode());
                    })
                    .body(GitHubPullRequestResponse.class);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("GitHub PR info request failed: {}/{}#{}",
                    parsedPrUrl.owner(), parsedPrUrl.repo(), parsedPrUrl.pullNumber(), ex);
            throw new BusinessException(ErrorCode.GITHUB_API_ERROR);
        }
    }

    public List<GitHubChangedFileResponse> listChangedFiles(ParsedPrUrl parsedPrUrl) {
        try {
            log.info("Fetching GitHub PR changed files: {}/{}#{}",
                    parsedPrUrl.owner(), parsedPrUrl.repo(), parsedPrUrl.pullNumber());

            return restClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{pullNumber}/files?per_page=100",
                            parsedPrUrl.owner(), parsedPrUrl.repo(), parsedPrUrl.pullNumber())
                    .headers(this::applyAuthHeader)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw toBusinessException(response.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<List<GitHubChangedFileResponse>>() {
                    });
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("GitHub PR changed files request failed: {}/{}#{}",
                    parsedPrUrl.owner(), parsedPrUrl.repo(), parsedPrUrl.pullNumber(), ex);
            throw new BusinessException(ErrorCode.GITHUB_API_ERROR);
        }
    }

    private void applyAuthHeader(HttpHeaders headers) {
        if (StringUtils.hasText(gitHubProperties.getToken())) {
            headers.setBearerAuth(gitHubProperties.getToken());
        }
    }

    private BusinessException toBusinessException(HttpStatusCode statusCode) {
        int status = statusCode.value();
        if (status == 401) {
            return new BusinessException(ErrorCode.GITHUB_TOKEN_INVALID);
        }
        if (status == 403) {
            return new BusinessException(ErrorCode.GITHUB_FORBIDDEN);
        }
        if (status == 404) {
            return new BusinessException(ErrorCode.GITHUB_PR_NOT_FOUND);
        }
        if (status == 422) {
            return new BusinessException(ErrorCode.GITHUB_PR_INVALID);
        }
        if (status >= 500) {
            return new BusinessException(ErrorCode.GITHUB_SERVICE_UNAVAILABLE);
        }
        return new BusinessException(ErrorCode.GITHUB_API_ERROR);
    }
}
