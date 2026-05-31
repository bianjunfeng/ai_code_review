package com.example.aipr.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("model_usage_log")
public class ModelUsageLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long fileId;

    private String skillCode;

    private String provider;

    private String modelName;

    private String callType;

    private Integer promptTokens = 0;

    private Integer completionTokens = 0;

    private Integer totalTokens = 0;

    private Long latencyMs;

    private Boolean success = true;

    @TableField(update = "NOW()")
    private String errorMessage;

    private java.math.BigDecimal estimatedCost;

    private String requestId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}