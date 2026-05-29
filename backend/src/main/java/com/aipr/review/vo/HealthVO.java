package com.aipr.review.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HealthVO {

    private String status;
    private String service;
}
