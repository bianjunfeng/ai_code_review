package com.example.aipr.enums;

public enum ErrorCode {

    SUCCESS(0, "success"),
    PARAM_ERROR(40000, "参数校验失败"),
    PR_URL_FORMAT_ERROR(40001, "PR 链接格式错误，请输入 GitHub Pull Request 地址"),
    SYSTEM_ERROR(50000, "系统异常，请稍后重试"),
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
