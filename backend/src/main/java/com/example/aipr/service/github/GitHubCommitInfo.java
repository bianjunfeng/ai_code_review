package com.example.aipr.service.github;

import lombok.Builder;
import lombok.Data;

/**
 * GitHub PR Commit 信息.
 */
@Data
@Builder
public class GitHubCommitInfo {

    /**
     * Commit SHA.
     */
    private String sha;

    /**
     * Commit message（仅取第一行摘要）.
     */
    private String message;

    /**
     * 提交作者 GitHub login 名.
     */
    private String author;

    /**
     * 提交时间，ISO 8601 格式.
     */
    private String date;

}