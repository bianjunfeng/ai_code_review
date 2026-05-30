package com.example.aipr.service.github;

import com.example.aipr.common.BusinessException;
import com.example.aipr.config.GitHubProperties;
import com.example.aipr.enums.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GitHubClient {

    private static final int PAGE_SIZE = 100;

    private final GitHubProperties gitHubProperties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public GitHubClient(GitHubProperties gitHubProperties, ObjectMapper objectMapper) {
        this.gitHubProperties = gitHubProperties;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(gitHubProperties.getConnectTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(gitHubProperties.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
    }

    public GitHubPrInfo getPullRequest(ParsedPrUrl parsedPrUrl) {
        HttpUrl url = buildBaseUrl()
                .addPathSegment("repos")
                .addPathSegment(parsedPrUrl.owner())
                .addPathSegment(parsedPrUrl.repo())
                .addPathSegment("pulls")
                .addPathSegment(String.valueOf(parsedPrUrl.pullNumber()))
                .build();

        JsonNode root = executeForJson(url, "GitHub PR detail");
        return GitHubPrInfo.builder()
                .owner(parsedPrUrl.owner())
                .repo(parsedPrUrl.repo())
                .pullNumber(parsedPrUrl.pullNumber())
                .title(root.path("title").asText(""))
                .description(root.path("body").asText(""))
                .author(root.path("user").path("login").asText("unknown"))
                .sourceBranch(root.path("head").path("ref").asText(""))
                .targetBranch(root.path("base").path("ref").asText(""))
                .state(root.path("state").asText("").toUpperCase(Locale.ROOT))
                .additions(root.path("additions").asInt(0))
                .deletions(root.path("deletions").asInt(0))
                .changedFiles(root.path("changed_files").asInt(0))
                .build();
    }

    public List<GitHubChangedFile> getPullRequestFiles(ParsedPrUrl parsedPrUrl) {
        List<GitHubChangedFile> files = new ArrayList<>();
        int page = 1;

        while (true) {
            HttpUrl url = buildBaseUrl()
                    .addPathSegment("repos")
                    .addPathSegment(parsedPrUrl.owner())
                    .addPathSegment(parsedPrUrl.repo())
                    .addPathSegment("pulls")
                    .addPathSegment(String.valueOf(parsedPrUrl.pullNumber()))
                    .addPathSegment("files")
                    .addQueryParameter("per_page", String.valueOf(PAGE_SIZE))
                    .addQueryParameter("page", String.valueOf(page))
                    .build();

            JsonNode root = executeForJson(url, "GitHub PR files");
            if (!root.isArray()) {
                throw new BusinessException(ErrorCode.GITHUB_API_ERROR, "GitHub 文件列表返回格式异常");
            }

            for (JsonNode fileNode : root) {
                files.add(GitHubChangedFile.builder()
                        .filename(fileNode.path("filename").asText(""))
                        .status(fileNode.path("status").asText(""))
                        .additions(fileNode.path("additions").asInt(0))
                        .deletions(fileNode.path("deletions").asInt(0))
                        .changes(fileNode.path("changes").asInt(0))
                        .patch(fileNode.path("patch").asText(""))
                        .build());
            }

            if (root.size() < PAGE_SIZE) {
                break;
            }
            page += 1;
        }

        return files;
    }

    private HttpUrl.Builder buildBaseUrl() {
        HttpUrl baseUrl = HttpUrl.parse(gitHubProperties.getApiBaseUrl());
        if (baseUrl == null) {
            throw new BusinessException(ErrorCode.GITHUB_API_ERROR, "GitHub API 地址配置错误");
        }
        return baseUrl.newBuilder();
    }

    private JsonNode executeForJson(HttpUrl url, String operationName) {
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");

        String token = gitHubProperties.getToken();
        if (token != null && !token.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw mapError(response, body);
            }
            return objectMapper.readTree(body);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.warn("{} failed: {}", operationName, e.getMessage());
            throw new BusinessException(ErrorCode.GITHUB_API_ERROR);
        }
    }

    private BusinessException mapError(Response response, String body) {
        int code = response.code();
        String message = extractMessage(body);
        if (code == 401) {
            return new BusinessException(ErrorCode.GITHUB_TOKEN_INVALID);
        }
        if (code == 403) {
            String remaining = response.header("X-RateLimit-Remaining");
            if ("0".equals(remaining)) {
                return new BusinessException(ErrorCode.GITHUB_RATE_LIMITED);
            }
            return new BusinessException(ErrorCode.GITHUB_REPOSITORY_NO_PERMISSION);
        }
        if (code == 404) {
            return new BusinessException(ErrorCode.GITHUB_PR_NOT_FOUND);
        }
        if (code == 422) {
            return new BusinessException(ErrorCode.PR_URL_FORMAT_ERROR);
        }
        if (message == null || message.isBlank()) {
            return new BusinessException(ErrorCode.GITHUB_API_ERROR);
        }
        return new BusinessException(ErrorCode.GITHUB_API_ERROR, "GitHub API 调用失败：" + message);
    }

    private String extractMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            return objectMapper.readTree(body).path("message").asText("");
        } catch (Exception e) {
            return "";
        }
    }
}
