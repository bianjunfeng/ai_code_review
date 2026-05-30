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
        if (shouldSkip(context.getFilePath())) {
            log.info("Skipping file for AI review: {}", context.getFilePath());
            return FileReviewResult.builder()
                    .filePath(context.getFilePath())
                    .summary("该文件类型暂不进行 AI Review")
                    .comments(Collections.emptyList())
                    .build();
        }

        if (context.getPatch() == null || context.getPatch().trim().isEmpty()) {
            log.info("Skipping file with empty patch: {}", context.getFilePath());
            return FileReviewResult.builder()
                    .filePath(context.getFilePath())
                    .summary("该文件无内容变更")
                    .comments(Collections.emptyList())
                    .build();
        }

        if (context.getLanguage() == null || context.getLanguage().isEmpty()) {
            context.setLanguage(promptRenderer.detectLanguage(context.getFilePath()));
        }

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
        String outputSummary = rawOutput.length() > MAX_LOG_LENGTH
                ? rawOutput.substring(0, MAX_LOG_LENGTH) + "..."
                : rawOutput;
        log.debug("LLM output for {}, length={}: {}", context.getFilePath(), rawOutput.length(), outputSummary);

        FileReviewResult result = outputParser.parseFileReview(rawOutput);
        result.setRawOutput(rawOutput);
        return result;
    }

    private boolean shouldSkip(String filePath) {
        if (filePath == null) {
            return true;
        }

        for (String dir : SKIP_DIRECTORIES) {
            if (filePath.contains(dir)) {
                return true;
            }
        }

        for (String skipFile : SKIP_FILES) {
            if (filePath.endsWith(skipFile)) {
                return true;
            }
        }

        for (String ext : SKIP_EXTENSIONS) {
            if (filePath.endsWith(ext)) {
                return true;
            }
        }

        return false;
    }
}
