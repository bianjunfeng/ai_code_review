package com.aipr.review.enums;

public enum ReviewTaskStatus {

    PENDING,
    FETCHING_PR,
    PARSING_DIFF,
    REVIEWING,
    SUMMARIZING,
    SUCCESS,
    FAILED,
    CANCELLED
}
