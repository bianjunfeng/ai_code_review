package com.aipr.review.enums;

public enum ErrorCode {

    SUCCESS(0, "success"),
    PARAM_ERROR(40000, "参数校验失败"),
    PR_URL_FORMAT_ERROR(40001, "PR 链接格式错误，请输入 GitHub Pull Request 地址"),
    GITHUB_TOKEN_INVALID(40101, "GitHub Token 无效，请检查 GITHUB_TOKEN"),
    GITHUB_FORBIDDEN(40301, "GitHub 仓库无权限或 API 限流，请检查 Token 或稍后重试"),
    GITHUB_PR_NOT_FOUND(40401, "GitHub 仓库或 PR 不存在"),
    GITHUB_PR_INVALID(42201, "PR 编号无效"),
    GITHUB_API_ERROR(50001, "GitHub API 调用失败，请稍后重试"),
    GITHUB_SERVICE_UNAVAILABLE(50002, "GitHub 服务异常，请稍后重试"),
    REVIEW_TASK_NOT_FOUND(40402, "Review 任务不存在"),
    SYSTEM_ERROR(50000, "系统异常，请稍后重试");

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
