package com.example.aipr.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("review_task")
public class ReviewTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String prUrl;

    private String ownerName;

    private String repoName;

    private Integer prNumber;

    private String prTitle;

    private String prDescription;

    private String prAuthor;

    private String sourceBranch;

    private String targetBranch;

    private String status;

    private Integer riskScore;

    private String riskLevel;

    private String summary;

    private String finalReview;

    private String resultJson;

    private String errorMessage;

    // 缓存相关字段
    private String headSha;
    private String baseSha;
    private String modelName;
    private String promptVersion;
    private Long cachedFromTaskId;

    /**
     * Commit 摘要，最多 10 条，以 "; " 分隔。
     * GitHub API 异常时为空字符串。
     */
    private String commitSummary;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
