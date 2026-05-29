package com.example.aipr.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateReviewTaskRequest {

    @NotBlank(message = "PR 链接不能为空")
    private String prUrl;
}
