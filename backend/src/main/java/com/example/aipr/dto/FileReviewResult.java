package com.example.aipr.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FileReviewResult {

    private String filePath;
    private String summary;
    private List<FileReviewCommentResult> comments;
}