package com.example.aipr.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("review_comment")
public class ReviewComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String filePath;

    private Integer lineNumber;

    private String riskType;

    private String riskLevel;

    private String title;

    private String description;

    private String reason;

    private String suggestion;

    private BigDecimal confidence;

    private Boolean needHumanCheck;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}