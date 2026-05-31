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

        log.debug("[GitHub] 获取 PR 信息, repo={}/{} PR#{}", parsedPrUrl.owner(), parsedPrUrl.repo(), parsedPrUrl.pullNumber());
        JsonNode root = executeForJson(url, "GitHub PR detail");
        log.debug("[GitHub] PR 信息获取成功, repo={}/{} PR#{} title={}", parsedPrUrl.owner(), parsedPrUrl.repo(), parsedPrUrl.pullNumber(), root.path("title").asText(""));

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
                .htmlUrl(root.path("html_url").asText(""))
                .draft(root.path("draft").asBoolean(false))
                .createdAt(root.path("created_at").asText(""))
                .updatedAt(root.path("updated_at").asText(""))
                .headSha(root.path("head").path("sha").asText(""))
                .baseSha(root.path("base").path("sha").asText(""))
                .additions(root.path("additions").asInt(0))
                .deletions(root.path("deletions").asInt(0))
                .changedFiles(root.path("changed_files").asInt(0))
                .build();
    }

    public List<GitHubPrInfo> listPullRequests(String owner, String repo, String state, int page, int pageSize) {
        HttpUrl url = buildBaseUrl()
                .addPathSegment("repos")
                .addPathSegment(owner)
                .addPathSegment(repo)
                .addPathSegment("pulls")
                .addQueryParameter("state", state)
                .addQueryParameter("per_page", String.valueOf(pageSize))
                .addQueryParameter("page", String.valueOf(page))
                .build();

        log.debug("[GitHub] 获取 PR 列表, repo={}/{}, state={}, page={}, pageSize={}", owner, repo, state, page, pageSize);
        JsonNode root = executeForJson(url, "GitHub PR list");
        if (!root.isArray()) {
            throw new BusinessException(ErrorCode.GITHUB_API_ERROR, "GitHub PR 列表返回格式异常");
        }

        List<GitHubPrInfo> pullRequests = new ArrayList<>();
        for (JsonNode node : root) {
            pullRequests.add(GitHubPrInfo.builder()
                    .owner(owner)
                    .repo(repo)
                    .pullNumber(node.path("number").asInt())
                    .title(node.path("title").asText(""))
                    .description(node.path("body").asText(""))
                    .author(node.path("user").path("login").asText("unknown"))
                    .sourceBranch(node.path("head").path("ref").asText(""))
                    .targetBranch(node.path("base").path("ref").asText(""))
                    .state(node.path("state").asText("").toUpperCase(Locale.ROOT))
                    .htmlUrl(node.path("html_url").asText(""))
                    .draft(node.path("draft").asBoolean(false))
                    .createdAt(node.path("created_at").asText(""))
                    .updatedAt(node.path("updated_at").asText(""))
                    .headSha(node.path("head").path("sha").asText(""))
                    .baseSha(node.path("base").path("sha").asText(""))
                    .build());
        }

        log.info("[GitHub] PR 列表获取完成, repo={}/{}, count={}", owner, repo, pullRequests.size());
        return pullRequests;
    }

    public List<GitHubChangedFile> getPullRequestFiles(ParsedPrUrl parsedPrUrl) {
        List<GitHubChangedFile> files = new ArrayList<>();
        int page = 1;

        log.debug("[GitHub] 获取 PR 文件列表, repo={}/{} PR#{}", parsedPrUrl.owner(), parsedPrUrl.repo(), parsedPrUrl.pullNumber());

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
                String filename = fileNode.path("filename").asText("");
                int additions = fileNode.path("additions").asInt(0);
                int deletions = fileNode.path("deletions").asInt(0);
                String patch = fileNode.path("patch").asText("");

                files.add(GitHubChangedFile.builder()
                        .filename(filename)
                        .status(fileNode.path("status").asText(""))
                        .additions(additions)
                        .deletions(deletions)
                        .changes(fileNode.path("changes").asInt(0))
                        .patch(patch)
                        .build());
            }

            int fileCount = files.size();
            log.debug("[GitHub] 第 {} 页获取完成, 当前累计文件数={}", page, fileCount);

            if (root.size() < PAGE_SIZE) {
                break;
            }
            page += 1;
        }

        log.info("[GitHub] PR 文件列表获取完成, repo={}/{} PR#{} 文件总数={}", parsedPrUrl.owner(), parsedPrUrl.repo(), parsedPrUrl.pullNumber(), files.size());
        return files;
    }

    /**
     * 获取 PR 的 commit 列表（最多 10 条）.
     */
    public List<GitHubCommitInfo> getPullRequestCommits(ParsedPrUrl parsedPrUrl) {
        List<GitHubCommitInfo> commits = new ArrayList<>();
        int page = 1;

        log.debug("[GitHub] 获取 PR commits, repo={}/{} PR#{}", parsedPrUrl.owner(), parsedPrUrl.repo(), parsedPrUrl.pullNumber());

        while (commits.size() < 10) {
            HttpUrl url = buildBaseUrl()
                    .addPathSegment("repos")
                    .addPathSegment(parsedPrUrl.owner())
                    .addPathSegment(parsedPrUrl.repo())
                    .addPathSegment("pulls")
                    .addPathSegment(String.valueOf(parsedPrUrl.pullNumber()))
                    .addPathSegment("commits")
                    .addQueryParameter("per_page", String.valueOf(PAGE_SIZE))
                    .addQueryParameter("page", String.valueOf(page))
                    .build();

            JsonNode root = executeForJson(url, "GitHub PR commits");
            if (!root.isArray()) {
                throw new BusinessException(ErrorCode.GITHUB_API_ERROR, "GitHub commits 返回格式异常");
            }

            for (JsonNode commitNode : root) {
                if (commits.size() >= 10) {
                    break;
                }
                String sha = commitNode.path("sha").asText("");
                String message = commitNode.path("commit").path("message").asText("");
                String author = commitNode.path("author").path("login").asText(
                        commitNode.path("commit").path("author").path("name").asText("unknown")
                );
                String date = commitNode.path("commit").path("author").path("date").asText("");

                // 只取第一行作为摘要
                String firstLine = message.isEmpty() ? "" : message.split("\n")[0];

                commits.add(GitHubCommitInfo.builder()
                        .sha(sha)
                        .message(firstLine)
                        .author(author)
                        .date(date)
                        .build());
            }

            if (root.size() < PAGE_SIZE) {
                break;
            }
            page += 1;
        }

        log.info("[GitHub] PR commits 获取完成, repo={}/{} PR#{} commitCount={}", parsedPrUrl.owner(), parsedPrUrl.repo(), parsedPrUrl.pullNumber(), commits.size());
        return commits;
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
