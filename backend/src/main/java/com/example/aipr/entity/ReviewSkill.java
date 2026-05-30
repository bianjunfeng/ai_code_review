package com.example.aipr.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("review_skill")
public class ReviewSkill {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String skillCode;

    private String skillName;

    private String skillType;

    private String description;

    private String supportedLanguages;

    private Boolean enabled;

    private Integer priority;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}