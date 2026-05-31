package com.example.aipr.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("review_file")
public class ReviewFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String filePath;

    private String fileStatus;

    private String language;

    private Integer additions;

    private Integer deletions;

    private Integer changes;

    private String patch;

    private Integer originalPatchLength;

    private Integer analyzedPatchLength;

    private Boolean truncated;

    private String aiSummary;

    private Boolean skipped;

    private String skipReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
