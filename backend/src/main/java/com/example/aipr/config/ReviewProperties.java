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

    @Data
    public static class Ai {
        /** 文件级并发数，默认 3 */
        private int fileReviewConcurrency = 3;
        /** 单文件 AI 分析超时秒数，默认 60 */
        private int fileReviewTimeoutSeconds = 60;
    }
}
