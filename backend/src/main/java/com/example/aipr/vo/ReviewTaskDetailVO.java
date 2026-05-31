package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewTaskDetailVO {

    private Long taskId;
    private String prUrl;
    private String prTitle;
    private String author;
    private String sourceBranch;
    private String targetBranch;
    private String status;
    private Integer riskScore;
    private String riskLevel;
    private String errorMessage;
    private String createdAt;
    private String updatedAt;

    /** 进度百分比 0-100 */
    private Integer progressPercent;
    /** 当前阶段中文描述 */
    private String currentStep;
    /** PR 总文件数 */
    private Integer totalFileCount;
    /** 已完成 AI 分析的文件数 */
    private Integer analyzedFileCount;
    /** 被跳过的文件数 */
    private Integer skippedFileCount;
    /** AI 调用失败的文件数 */
    private Integer failedFileCount;

    /** 是否命中数据库缓存 */
    private Boolean cached;
    /** 缓存来源任务 ID */
    private Long cachedFromTaskId;
    /** 评审使用的模型名称 */
    private String modelName;
    /** Prompt 版本 */
    private String promptVersion;
}
