package com.example.aipr.enums;

public enum ErrorCode {

    SUCCESS(0, "success"),
    PARAM_ERROR(40000, "参数校验失败"),
    PR_URL_FORMAT_ERROR(40001, "PR 链接格式错误，请输入 GitHub Pull Request 地址"),
    REVIEW_TASK_NOT_FOUND(40401, "Review 任务不存在"),
    GITHUB_API_ERROR(50100, "GitHub API 调用失败，请稍后重试"),
    GITHUB_TOKEN_INVALID(50101, "GitHub Token 无效，请检查配置"),
    GITHUB_REPOSITORY_NO_PERMISSION(50102, "GitHub 仓库无权限，请检查 Token 或仓库访问权限"),
    GITHUB_PR_NOT_FOUND(50103, "GitHub PR 不存在或无访问权限"),
    GITHUB_RATE_LIMITED(50104, "GitHub API 限流，请稍后重试"),
    SYSTEM_ERROR(50000, "系统异常，请稍后重试"),
    TOO_MANY_REQUESTS(42900, "请求过于频繁，请稍后再试"),
    AI_SERVICE_ERROR(50201, "AI 服务调用失败，请稍后重试"),
    AI_RESPONSE_PARSE_ERROR(50202, "模型返回格式异常，请重新评审");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
