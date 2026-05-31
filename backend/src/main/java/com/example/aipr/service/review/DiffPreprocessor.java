package com.example.aipr.service.review;

import com.example.aipr.config.ReviewProperties;
import com.example.aipr.service.github.GitHubChangedFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
@RequiredArgsConstructor
public class DiffPreprocessor {

    private final ReviewProperties reviewProperties;

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
    public static final String REASON_FILE_COUNT_LIMIT = "FILE_COUNT_LIMIT";
    public static final String REASON_TOTAL_PATCH_LIMIT = "TOTAL_PATCH_LIMIT";

    private static final List<String> HIGH_PRIORITY_EXTENSIONS = Arrays.asList(
            ".java", ".kt", ".kts", ".go", ".rs", ".py", ".js", ".jsx",
            ".ts", ".tsx", ".vue", ".c", ".h", ".cpp", ".cc", ".cxx",
            ".cs", ".php", ".rb", ".swift", ".scala", ".sql"
    );

    private static final List<String> MEDIUM_PRIORITY_EXTENSIONS = Arrays.asList(
            ".xml", ".yml", ".yaml", ".json", ".properties", ".toml", ".gradle"
    );

    private static final List<String> LOW_PRIORITY_EXTENSIONS = Arrays.asList(
            ".md", ".txt", ".rst", ".adoc"
    );

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
     * 基于文件类型、单文件长度和 PR 总 patch 预算生成最终保存决策。
     */
    public List<PreparedFile> preprocess(List<GitHubChangedFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        int maxFiles = Math.max(0, reviewProperties.getDiff().getMaxFiles());
        int maxFilePatchChars = Math.max(0, reviewProperties.getDiff().getMaxFilePatchChars());
        int maxTotalPatchChars = Math.max(0, reviewProperties.getDiff().getMaxTotalPatchChars());

        List<PreparedFile> prepared = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();

        for (int index = 0; index < files.size(); index++) {
            GitHubChangedFile file = files.get(index);
            Decision decision = evaluate(file);
            int originalPatchLength = patchLength(file);

            if (decision.skipped()) {
                prepared.add(PreparedFile.skip(file, decision.skipReason(), originalPatchLength));
                continue;
            }

            prepared.add(null);
            candidates.add(new Candidate(index, file, originalPatchLength, priority(file.getFilename())));
        }

        candidates.sort(Comparator
                .comparingInt(Candidate::priority)
                .thenComparingInt(Candidate::index));

        int analyzedFileCount = 0;
        int totalPatchChars = 0;
        for (Candidate candidate : candidates) {
            GitHubChangedFile file = candidate.file();
            if (analyzedFileCount >= maxFiles) {
                prepared.set(candidate.index(), PreparedFile.skip(file, REASON_FILE_COUNT_LIMIT, candidate.originalPatchLength()));
                log.info("[DiffPreprocessor] 超过最大分析文件数, path={}, maxFiles={}", file.getFilename(), maxFiles);
                continue;
            }

            String originalPatch = file.getPatch();
            String analyzedPatch = truncatePatch(originalPatch, maxFilePatchChars);
            int analyzedPatchLength = analyzedPatch == null ? 0 : analyzedPatch.length();
            boolean truncated = originalPatch != null && analyzedPatchLength < originalPatch.length();

            if (totalPatchChars + analyzedPatchLength > maxTotalPatchChars) {
                prepared.set(candidate.index(), PreparedFile.skip(file, REASON_TOTAL_PATCH_LIMIT, candidate.originalPatchLength()));
                log.info("[DiffPreprocessor] 超过 PR patch 总预算, path={}, currentTotal={}, patchLength={}, maxTotal={}",
                        file.getFilename(), totalPatchChars, analyzedPatchLength, maxTotalPatchChars);
                continue;
            }

            totalPatchChars += analyzedPatchLength;
            analyzedFileCount++;
            prepared.set(candidate.index(), PreparedFile.analyze(file, analyzedPatch, candidate.originalPatchLength(), analyzedPatchLength, truncated));
        }

        return prepared;
    }

    private String truncatePatch(String patch, int maxFilePatchChars) {
        if (patch == null) {
            return null;
        }
        if (patch.length() <= maxFilePatchChars) {
            return patch;
        }
        return patch.substring(0, maxFilePatchChars);
    }

    private int patchLength(GitHubChangedFile file) {
        return file.getPatch() == null ? 0 : file.getPatch().length();
    }

    private int priority(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return 100;
        }

        String lowerPath = filePath.toLowerCase();
        if (isTestPath(lowerPath)) {
            return 20;
        }
        if (endsWithAny(lowerPath, HIGH_PRIORITY_EXTENSIONS)) {
            return 10;
        }
        if (endsWithAny(lowerPath, MEDIUM_PRIORITY_EXTENSIONS)) {
            return 40;
        }
        if (endsWithAny(lowerPath, LOW_PRIORITY_EXTENSIONS)) {
            return 80;
        }
        return 60;
    }

    private boolean isTestPath(String lowerPath) {
        return lowerPath.contains("/test/")
                || lowerPath.contains("\\test\\")
                || lowerPath.contains("__tests__/")
                || lowerPath.endsWith("test.java")
                || lowerPath.endsWith("spec.ts")
                || lowerPath.endsWith("spec.js")
                || lowerPath.endsWith(".test.ts")
                || lowerPath.endsWith(".test.js");
    }

    private boolean endsWithAny(String lowerPath, List<String> suffixes) {
        for (String suffix : suffixes) {
            if (lowerPath.endsWith(suffix)) {
                return true;
            }
        }
        return false;
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

    private record Candidate(int index, GitHubChangedFile file, int originalPatchLength, int priority) {
    }

    public record PreparedFile(
            GitHubChangedFile file,
            String analyzedPatch,
            int originalPatchLength,
            int analyzedPatchLength,
            boolean truncated,
            boolean skipped,
            String skipReason
    ) {

        public static PreparedFile analyze(GitHubChangedFile file,
                                           String analyzedPatch,
                                           int originalPatchLength,
                                           int analyzedPatchLength,
                                           boolean truncated) {
            return new PreparedFile(file, analyzedPatch, originalPatchLength, analyzedPatchLength, truncated, false, null);
        }

        public static PreparedFile skip(GitHubChangedFile file, String skipReason, int originalPatchLength) {
            return new PreparedFile(file, null, originalPatchLength, 0, false, true, skipReason);
        }
    }
}
