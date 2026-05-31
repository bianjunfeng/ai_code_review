package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewTaskCreatedVO {

    private Long taskId;
    private String status;
    private Boolean cached;
    private Long cachedFromTaskId;
}
