package com.example.aipr.enums;

public enum ReviewTaskStatus {

    PENDING,
    FETCHING_PR,
    PARSING_DIFF,
    REVIEWING,
    SUMMARIZING,
    SCORING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
    CANCELLED
}
