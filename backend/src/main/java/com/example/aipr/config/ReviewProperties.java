package com.example.aipr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 评审相关配置。
 *
 * <p>对应文档：TODO-评审耗时优化设计方案 六、P1 建议实现</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "review")
public class ReviewProperties {

    private Ai ai = new Ai();
    private Diff diff = new Diff();

    @Data
    public static class Ai {
        /** 文件级并发数，默认 3 */
        private int fileReviewConcurrency = 3;
        /** 单文件 AI 分析超时秒数，默认 60 */
        private int fileReviewTimeoutSeconds = 60;
    }

    @Data
    public static class Diff {
        /** 单个 PR 最多进入 AI 分析的文件数 */
        private int maxFiles = 30;
        /** 单文件 patch 最大分析字符数 */
        private int maxFilePatchChars = 12000;
        /** 单个 PR 累计 patch 最大分析字符数 */
        private int maxTotalPatchChars = 100000;
    }
}
