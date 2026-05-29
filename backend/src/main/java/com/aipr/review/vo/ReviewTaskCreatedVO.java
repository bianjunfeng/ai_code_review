package com.aipr.review.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewTaskCreatedVO {

    private Long taskId;
    private String status;
}
