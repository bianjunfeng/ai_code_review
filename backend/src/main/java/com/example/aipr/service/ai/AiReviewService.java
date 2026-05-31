package com.example.aipr.service.ai;

import com.example.aipr.common.BusinessException;
import com.example.aipr.dto.AiReviewContext;
import com.example.aipr.dto.FileReviewResult;
import com.example.aipr.dto.PrSummaryResult;
import com.example.aipr.enums.ErrorCode;
import com.example.aipr.service.prompt.AiReviewOutputParser;
import com.example.aipr.service.prompt.PromptRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

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

        LlmCallContext callContext = LlmCallContext.builder()
                .taskId(context.getTaskId())
                .fileId(context.getFileId())
                .skillCode(null)
                .callType("FILE_REVIEW")
                .build();
        LlmResponse llmResponse = llmClient.chat(llmRequest, callContext);

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

    /**
     * 调用 LLM 生成 PR 级别总结。
     * 失败时返回 null，不抛出异常。
     */
    public PrSummaryResult summarizePr(String prTitle, String prAuthor,
                                    String sourceBranch, String targetBranch,
                                    String commitSummary,
                                    List<String> fileSummaries,
                                    int analyzedCount, int skippedCount,
                                    int failedCount, int truncatedCount,
                                    int criticalCount, int highCount,
                                    int mediumCount, int lowCount,
                                    Long taskId) {
        String prompt = promptRenderer.renderPrSummaryPrompt(
                prTitle, prAuthor, sourceBranch, targetBranch, commitSummary,
                fileSummaries, analyzedCount, skippedCount, failedCount, truncatedCount,
                criticalCount, highCount, mediumCount, lowCount);

        LlmRequest llmRequest = LlmRequest.builder()
                .messages(List.of(
                        LlmMessage.builder()
                                .role("user")
                                .content(prompt)
                                .build()
                ))
                .build();

        LlmCallContext callContext = LlmCallContext.builder()
                .taskId(taskId)
                .fileId(null)
                .skillCode(null)
                .callType("PR_SUMMARY")
                .build();

        LlmResponse llmResponse;
        try {
            llmResponse = llmClient.chat(llmRequest, callContext);
        } catch (Exception e) {
            log.warn("[AI] PR 总结调用失败, taskId={}, error={}", taskId, e.getMessage());
            return null;
        }

        String rawOutput = llmResponse.getContent();
        log.info("[AI] PR 总结生成完成, taskId={}, responseLength={}", taskId, rawOutput.length());

        return parsePrSummary(rawOutput);
    }

    private PrSummaryResult parsePrSummary(String rawOutput) {
        try {
            String cleaned = rawOutput.trim();
            // 清理 Markdown 代码块标记
            if (cleaned.startsWith("```")) {
                int firstNewline = cleaned.indexOf('\n');
                int lastBackticks = cleaned.lastIndexOf("```");
                if (lastBackticks > firstNewline) {
                    cleaned = cleaned.substring(firstNewline + 1, lastBackticks).trim();
                }
            }
            JsonNode node = objectMapper.readTree(cleaned);
            return PrSummaryResult.builder()
                    .summary(nullToEmpty(node.path("summary").asText()))
                    .finalReview(nullToEmpty(node.path("finalReview").asText()))
                    .testSuggestions(Collections.emptyList())
                    .build();
        } catch (Exception e) {
            log.warn("[AI] PR 总结 JSON 解析失败, rawOutput={}", rawOutput.length() > 200
                    ? rawOutput.substring(0, 200) : rawOutput);
            return null;
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
