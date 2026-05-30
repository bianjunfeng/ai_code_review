package com.example.aipr.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("review_skill_result")
public class ReviewSkillResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String skillName;

    private Boolean success;

    private String summary;

    private String rawOutput;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}