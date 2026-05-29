package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HealthVO {

    private String status;
    private String service;
}
