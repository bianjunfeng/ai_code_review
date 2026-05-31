package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewMarkdownVO {

    private Long taskId;
    private String markdown;
}
