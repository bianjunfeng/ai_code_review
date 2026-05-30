package com.example.aipr.service.prompt;

import com.example.aipr.common.BusinessException;
import com.example.aipr.dto.FileReviewCommentResult;
import com.example.aipr.dto.FileReviewResult;
import com.example.aipr.enums.ErrorCode;
import com.example.aipr.enums.RiskType;
import com.example.aipr.enums.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AiReviewOutputParser {

    private static final int MAX_LOG_LENGTH = 200;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileReviewResult parseFileReview(String rawOutput) {
        if (rawOutput == null || rawOutput.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_PARSE_ERROR, "模型返回为空，请重新评审");
        }

        String cleaned = cleanMarkdownCodeBlocks(rawOutput.trim());

        try {
            JsonNode root = objectMapper.readTree(cleaned);
            return parseJsonToResult(root);
        } catch (Exception e) {
            log.warn("Failed to parse AI output: {}", truncateForLog(rawOutput));
            throw new BusinessException(ErrorCode.AI_RESPONSE_PARSE_ERROR, "模型返回格式异常，请重新评审");
        }
    }

    private String cleanMarkdownCodeBlocks(String raw) {
        String result = raw;

        if (result.startsWith("```json")) {
            result = result.substring(7);
        } else if (result.startsWith("```")) {
            result = result.substring(3);
        }

        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3);
        }

        result = result.trim();

        int firstBrace = result.indexOf('{');
        int lastBrace = result.lastIndexOf('}');

        if (firstBrace >= 0 && lastBrace > firstBrace) {
            result = result.substring(firstBrace, lastBrace + 1);
        }

        return result.trim();
    }

    private FileReviewResult parseJsonToResult(JsonNode root) {
        FileReviewResult.FileReviewResultBuilder builder = FileReviewResult.builder();

        if (root.has("filePath") && !root.get("filePath").isNull()) {
            builder.filePath(root.get("filePath").asText());
        }

        if (root.has("summary") && !root.get("summary").isNull()) {
            builder.summary(root.get("summary").asText());
        } else {
            builder.summary("该文件暂无明确总结");
        }

        List<FileReviewCommentResult> comments = new ArrayList<>();
        if (root.has("comments") && root.get("comments").isArray()) {
            for (JsonNode commentNode : root.get("comments")) {
                FileReviewCommentResult comment = parseComment(commentNode);
                comments.add(comment);
            }
        }
        builder.comments(comments);

        return builder.build();
    }

    private FileReviewCommentResult parseComment(JsonNode commentNode) {
        if (commentNode == null || commentNode.isNull()) {
            return FileReviewCommentResult.builder()
                    .riskType(RiskType.MAINTAINABILITY.name())
                    .severity(Severity.INFO.name())
                    .title("代码评审建议")
                    .description("模型未返回详细描述，请人工确认")
                    .suggestion("建议人工复核该变更")
                    .confidence(0.5)
                    .needHumanCheck(true)
                    .build();
        }

        FileReviewCommentResult.FileReviewCommentResultBuilder builder = FileReviewCommentResult.builder();

        if (!commentNode.isNull() && commentNode.has("line") && !commentNode.get("line").isNull()) {
            try {
                builder.line(commentNode.get("line").asInt());
            } catch (Exception e) {
                // 忽略无效的 line 值
            }
        }

        if (commentNode.has("riskType") && !commentNode.get("riskType").isNull()) {
            String riskType = commentNode.get("riskType").asText();
            try {
                builder.riskType(RiskType.valueOf(riskType).name());
            } catch (IllegalArgumentException e) {
                builder.riskType(RiskType.MAINTAINABILITY.name());
            }
        } else {
            builder.riskType(RiskType.MAINTAINABILITY.name());
        }

        if (commentNode.has("severity") && !commentNode.get("severity").isNull()) {
            String severity = commentNode.get("severity").asText();
            try {
                builder.severity(Severity.valueOf(severity).name());
            } catch (IllegalArgumentException e) {
                builder.severity(Severity.INFO.name());
            }
        } else {
            builder.severity(Severity.INFO.name());
        }

        if (commentNode.has("title") && !commentNode.get("title").isNull()) {
            builder.title(commentNode.get("title").asText());
        } else {
            builder.title("代码评审建议");
        }

        if (commentNode.has("description") && !commentNode.get("description").isNull()) {
            builder.description(commentNode.get("description").asText());
        } else {
            builder.description("模型未返回详细描述，请人工确认");
        }

        if (commentNode.has("suggestion") && !commentNode.get("suggestion").isNull()) {
            builder.suggestion(commentNode.get("suggestion").asText());
        } else {
            builder.suggestion("建议人工复核该变更");
        }

        double confidence = 0.5;
        if (commentNode.has("confidence") && !commentNode.get("confidence").isNull()) {
            try {
                confidence = commentNode.get("confidence").asDouble();
            } catch (Exception e) {
                confidence = 0.5;
            }
        }
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        builder.confidence(confidence);

        boolean needHumanCheck = true;
        if (commentNode.has("needHumanCheck") && !commentNode.get("needHumanCheck").isNull()) {
            try {
                needHumanCheck = commentNode.get("needHumanCheck").asBoolean();
            } catch (Exception e) {
                needHumanCheck = true;
            }
        }
        builder.needHumanCheck(needHumanCheck);

        return builder.build();
    }

    private String truncateForLog(String content) {
        if (content == null) {
            return "null";
        }
        if (content.length() <= MAX_LOG_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_LOG_LENGTH) + "... [truncated]";
    }
}