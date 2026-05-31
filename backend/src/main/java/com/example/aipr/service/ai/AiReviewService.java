package com.example.aipr.service.ai;

import com.example.aipr.common.BusinessException;
import com.example.aipr.dto.AiReviewContext;
import com.example.aipr.dto.FileReviewResult;
import com.example.aipr.enums.ErrorCode;
import com.example.aipr.service.prompt.AiReviewOutputParser;
import com.example.aipr.service.prompt.PromptRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReviewService {

    private final PromptRenderer promptRenderer;
    private final AiReviewOutputParser outputParser;
    private final LlmClient llmClient;

    private static final int MAX_LOG_LENGTH = 200;

    private static final List<String> SKIP_EXTENSIONS = Arrays.asList(
            ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico",
            ".zip", ".jar", ".class", ".min.js"
    );

    private static final List<String> SKIP_FILES = Arrays.asList(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml"
    );

    private static final List<String> SKIP_DIRECTORIES = Arrays.asList(
            "dist/", "target/", "node_modules/"
    );

    public FileReviewResult reviewFile(AiReviewContext context) {
        String filePath = context.getFilePath();

        if (shouldSkip(filePath)) {
            log.info("[AI] 跳过文件, path={}", filePath);
            return FileReviewResult.builder()
                    .filePath(filePath)
                    .summary("该文件类型暂不进行 AI Review")
                    .comments(Collections.emptyList())
                    .build();
        }

        if (context.getPatch() == null || context.getPatch().trim().isEmpty()) {
            log.info("[AI] 跳过空 patch 文件, path={}", filePath);
            return FileReviewResult.builder()
                    .filePath(filePath)
                    .summary("该文件无内容变更")
                    .comments(Collections.emptyList())
                    .build();
        }

        if (context.getLanguage() == null || context.getLanguage().isEmpty()) {
            context.setLanguage(promptRenderer.detectLanguage(filePath));
        }

        log.info("[AI] 开始分析文件, path={}, language={}, patchLength={}", filePath, context.getLanguage(), context.getPatch().length());

        String prompt = promptRenderer.renderFileReviewPrompt(context);

        LlmRequest llmRequest = LlmRequest.builder()
                .messages(List.of(
                        LlmMessage.builder()
                                .role("user")
                                .content(prompt)
                                .build()
                ))
                .build();

        LlmResponse llmResponse = llmClient.chat(llmRequest);

        String rawOutput = llmResponse.getContent();
        log.info("[AI] 文件分析完成, path={}, responseLength={}", filePath, rawOutput.length());

        FileReviewResult result = outputParser.parseFileReview(rawOutput);
        result.setRawOutput(rawOutput);

        int commentCount = result.getComments() != null ? result.getComments().size() : 0;
        log.info("[AI] 文件分析结果, path={}, summary={}, commentCount={}", filePath, result.getSummary(), commentCount);

        return result;
    }

    private boolean shouldSkip(String filePath) {
        if (filePath == null) {
            return true;
        }

        // ── 安全网：DiffPreprocessor 已在上游处理了绝大多数跳过逻辑 ──
        // 这里保留一份最小化检查，防止意外遗漏

        for (String dir : SKIP_DIRECTORIES) {
            if (filePath.contains(dir)) {
                log.debug("[AI] 跳过目录匹配文件（安全网）, path={}", filePath);
                return true;
            }
        }

        for (String skipFile : SKIP_FILES) {
            if (filePath.endsWith(skipFile)) {
                log.debug("[AI] 跳过 lock 文件（安全网）, path={}", filePath);
                return true;
            }
        }

        for (String ext : SKIP_EXTENSIONS) {
            if (filePath.endsWith(ext)) {
                log.debug("[AI] 跳过二进制/生成文件（安全网）, path={}", filePath);
                return true;
            }
        }

        return false;
    }
}
