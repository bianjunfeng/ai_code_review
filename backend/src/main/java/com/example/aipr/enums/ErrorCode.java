package com.example.aipr.enums;

public enum ErrorCode {

    SUCCESS(0, "success"),
    PARAM_ERROR(40000, "参数校验失败"),
    PR_URL_FORMAT_ERROR(40001, "PR 链接格式错误，请输入 GitHub Pull Request 地址"),
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
