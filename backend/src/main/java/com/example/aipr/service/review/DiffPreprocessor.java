package com.example.aipr.service.review;

import com.example.aipr.service.github.GitHubChangedFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Diff 预处理：在保存文件到 DB 之前决定是否跳过 AI 分析。
 * 将原来散落在 AiReviewService.shouldSkip() 中的逻辑集中到这里，
 * 确保 skipped / skipReason 在入库时就确定好。
 *
 * <p>对应文档：TODO-评审耗时优化设计方案 P0-5.4</p>
 */
@Slf4j
@Component
public class DiffPreprocessor {

    // ── 目录模式：路径中包含这些目录直接跳过 ──
    private static final List<String> SKIP_DIRECTORIES = Arrays.asList(
            "node_modules/",
            "dist/",
            "build/",
            "target/",
            ".git/"
    );

    // ── 文件名精确匹配 ──
    private static final List<String> SKIP_FILES = Arrays.asList(
            "package-lock.json",
            "yarn.lock",
            "pnpm-lock.yaml"
    );

    // ── 扩展名：二进制、压缩、图片等不适合 AI 审查的文件 ──
    private static final List<String> SKIP_EXTENSIONS = Arrays.asList(
            ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico",
            ".pdf", ".zip", ".jar", ".class",
            ".min.js", ".map"
    );

    // ── 跳过原因常量 ──
    public static final String REASON_LOCK_FILE = "LOCK_FILE";
    public static final String REASON_BINARY_FILE = "BINARY_FILE";
    public static final String REASON_GENERATED_FILE = "GENERATED_FILE";
    public static final String REASON_PATCH_EMPTY = "PATCH_EMPTY";
    public static final String REASON_UNSUPPORTED_FILE_TYPE = "UNSUPPORTED_FILE_TYPE";

    /**
     * 判断文件是否需要跳过 AI Review。
     *
     * @param file GitHub 变更文件
     * @return 预处理结果，包含是否跳过及原因
     */
    public Decision evaluate(GitHubChangedFile file) {
        String filePath = file.getFilename();

        if (filePath == null) {
            return Decision.skip(REASON_UNSUPPORTED_FILE_TYPE);
        }

        // 1. 目录匹配
        for (String dir : SKIP_DIRECTORIES) {
            if (filePath.contains(dir)) {
                log.debug("[DiffPreprocessor] 跳过目录匹配文件, path={}, reason={}", filePath, REASON_GENERATED_FILE);
                return Decision.skip(REASON_GENERATED_FILE);
            }
        }

        // 2. 文件名精确匹配（lock 文件等）
        for (String skipFile : SKIP_FILES) {
            if (filePath.endsWith(skipFile)) {
                log.debug("[DiffPreprocessor] 跳过 lock 文件, path={}, reason={}", filePath, REASON_LOCK_FILE);
                return Decision.skip(REASON_LOCK_FILE);
            }
        }

        // 3. 扩展名匹配（图片、二进制等）
        for (String ext : SKIP_EXTENSIONS) {
            if (filePath.toLowerCase().endsWith(ext)) {
                log.debug("[DiffPreprocessor] 跳过二进制/生成文件, path={}, reason={}", filePath, REASON_BINARY_FILE);
                return Decision.skip(REASON_BINARY_FILE);
            }
        }

        // 4. 空 patch
        String patch = file.getPatch();
        if (patch == null || patch.trim().isEmpty()) {
            log.debug("[DiffPreprocessor] 跳过空 patch 文件, path={}", filePath);
            return Decision.skip(REASON_PATCH_EMPTY);
        }

        return Decision.analyze();
    }

    /**
     * 预处理决策。
     */
    public record Decision(boolean skipped, String skipReason) {

        private static final Decision ANALYZE_INSTANCE = new Decision(false, null);

        public static Decision analyze() {
            return ANALYZE_INSTANCE;
        }

        public static Decision skip(String reason) {
            return new Decision(true, reason);
        }
    }
}
