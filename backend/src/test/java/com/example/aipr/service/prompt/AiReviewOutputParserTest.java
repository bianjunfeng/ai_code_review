package com.example.aipr.service.prompt;

import com.example.aipr.common.BusinessException;
import com.example.aipr.dto.FileReviewResult;
import com.example.aipr.enums.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiReviewOutputParserTest {

    private final AiReviewOutputParser parser = new AiReviewOutputParser();

    @Test
    void parseFileReview_validJson_returnsResult() {
        String rawOutput = """
                {
                  "filePath": "src/main/java/AuthService.java",
                  "summary": "该文件新增了登录校验逻辑",
                  "comments": [
                    {
                      "line": 42,
                      "riskType": "SECURITY_RISK",
                      "severity": "HIGH",
                      "title": "密码明文比较",
                      "description": "当前代码直接比较明文密码",
                      "reason": "明文比较无法抵御密码泄露后的重放风险",
                      "evidence": "if (password.equals(user.getPassword()))",
                      "actionLevel": "MUST_FIX",
                      "suggestion": "建议使用 BCryptPasswordEncoder",
                      "confidence": 0.92,
                      "needHumanCheck": true
                    }
                  ]
                }
                """;

        FileReviewResult result = parser.parseFileReview(rawOutput);

        assertEquals("src/main/java/AuthService.java", result.getFilePath());
        assertEquals("该文件新增了登录校验逻辑", result.getSummary());
        assertEquals(1, result.getComments().size());
        assertEquals("SECURITY_RISK", result.getComments().get(0).getRiskType());
        assertEquals("HIGH", result.getComments().get(0).getSeverity());
        assertEquals("明文比较无法抵御密码泄露后的重放风险", result.getComments().get(0).getReason());
        assertEquals("if (password.equals(user.getPassword()))", result.getComments().get(0).getEvidence());
        assertEquals("MUST_FIX", result.getComments().get(0).getActionLevel());
        assertEquals(0.92, result.getComments().get(0).getConfidence());
    }

    @Test
    void parseFileReview_markdownWrappedJson_parsesCorrectly() {
        String rawOutput = """
                ```json
                {
                  "filePath": "test.java",
                  "summary": "Test summary",
                  "comments": []
                }
                ```
                """;

        FileReviewResult result = parser.parseFileReview(rawOutput);

        assertEquals("test.java", result.getFilePath());
        assertEquals("Test summary", result.getSummary());
        assertTrue(result.getComments().isEmpty());
    }

    @Test
    void parseFileReview_backticksOnly_parsesCorrectly() {
        String rawOutput = """
                ```
                {"filePath": "Example.java", "summary": "Example", "comments": []}
                ```
                """;

        FileReviewResult result = parser.parseFileReview(rawOutput);

        assertEquals("Example.java", result.getFilePath());
    }

    @Test
    void parseFileReview_emptyComments_returnsEmptyList() {
        String rawOutput = """
                {"filePath": "Empty.java", "summary": "No comments", "comments": []}
                """;

        FileReviewResult result = parser.parseFileReview(rawOutput);

        assertTrue(result.getComments().isEmpty());
    }

    @Test
    void parseFileReview_confidenceOutOfRange_normalized() {
        String rawOutput = """
                {
                  "filePath": "Test.java",
                  "summary": "Test",
                  "comments": [
                    {
                      "riskType": "BUG_RISK",
                      "severity": "HIGH",
                      "title": "Test",
                      "confidence": 1.5,
                      "needHumanCheck": false
                    }
                  ]
                }
                """;

        FileReviewResult result = parser.parseFileReview(rawOutput);

        assertEquals(1.0, result.getComments().get(0).getConfidence());
    }

    @Test
    void parseFileReview_confidenceNegative_normalized() {
        String rawOutput = """
                {
                  "filePath": "Test.java",
                  "summary": "Test",
                  "comments": [
                    {
                      "riskType": "BUG_RISK",
                      "severity": "HIGH",
                      "title": "Test",
                      "confidence": -0.5,
                      "needHumanCheck": false
                    }
                  ]
                }
                """;

        FileReviewResult result = parser.parseFileReview(rawOutput);

        assertEquals(0.0, result.getComments().get(0).getConfidence());
    }

    @Test
    void parseFileReview_invalidRiskType_defaultsToMaintainability() {
        String rawOutput = """
                {
                  "filePath": "Test.java",
                  "summary": "Test",
                  "comments": [
                    {
                      "riskType": "INVALID_TYPE",
                      "severity": "HIGH",
                      "title": "Test"
                    }
                  ]
                }
                """;

        FileReviewResult result = parser.parseFileReview(rawOutput);

        assertEquals("MAINTAINABILITY", result.getComments().get(0).getRiskType());
    }

    @Test
    void parseFileReview_invalidSeverity_defaultsToInfo() {
        String rawOutput = """
                {
                  "filePath": "Test.java",
                  "summary": "Test",
                  "comments": [
                    {
                      "riskType": "BUG_RISK",
                      "severity": "INVALID",
                      "title": "Test"
                    }
                  ]
                }
                """;

        FileReviewResult result = parser.parseFileReview(rawOutput);

        assertEquals("INFO", result.getComments().get(0).getSeverity());
    }

    @Test
    void parseFileReview_riskLevelAlias_parsesAsSeverity() {
        String rawOutput = """
                {
                  "filePath": "Critical.java",
                  "summary": "Test",
                  "comments": [
                    {
                      "riskType": "SECURITY_RISK",
                      "riskLevel": "CRITICAL",
                      "title": "Test"
                    }
                  ]
                }
                """;

        FileReviewResult result = parser.parseFileReview(rawOutput);

        assertEquals("CRITICAL", result.getComments().get(0).getSeverity());
    }

    @Test
    void parseFileReview_missingTitle_defaultsToCodeReviewSuggestion() {
        String rawOutput = """
                {
                  "filePath": "Test.java",
                  "summary": "Test",
                  "comments": [
                    {
                      "riskType": "BUG_RISK",
                      "severity": "HIGH"
                    }
                  ]
                }
                """;

        FileReviewResult result = parser.parseFileReview(rawOutput);

        assertEquals("代码评审建议", result.getComments().get(0).getTitle());
    }

    @Test
    void parseFileReview_missingDescription_hasDefault() {
        String rawOutput = """
                {
                  "filePath": "Test.java",
                  "summary": "Test",
                  "comments": [
                    {
                      "riskType": "BUG_RISK",
                      "severity": "HIGH",
                      "title": "Test Title"
                    }
                  ]
                }
                """;

        FileReviewResult result = parser.parseFileReview(rawOutput);

        assertEquals("模型未返回详细描述，请人工确认", result.getComments().get(0).getDescription());
    }

    @Test
    void parseFileReview_missingEvidenceFields_defaultsWithoutBreakingOldFormat() {
        String rawOutput = """
                {
                  "filePath": "Legacy.java",
                  "summary": "Legacy output",
                  "comments": [
                    {
                      "riskType": "BUG_RISK",
                      "severity": "MEDIUM",
                      "title": "Legacy"
                    }
                  ]
                }
                """;

        FileReviewResult result = parser.parseFileReview(rawOutput);

        assertEquals("模型未返回风险依据，请人工确认", result.getComments().get(0).getReason());
        assertEquals("模型未返回证据，请结合 diff 人工确认", result.getComments().get(0).getEvidence());
        assertEquals("SHOULD_FIX", result.getComments().get(0).getActionLevel());
    }

    @Test
    void parseFileReview_invalidActionLevel_defaultsBySeverity() {
        String rawOutput = """
                {
                  "filePath": "Action.java",
                  "summary": "Action",
                  "comments": [
                    {
                      "riskType": "SECURITY_RISK",
                      "severity": "HIGH",
                      "title": "Action",
                      "actionLevel": "BLOCKER"
                    }
                  ]
                }
                """;

        FileReviewResult result = parser.parseFileReview(rawOutput);

        assertEquals("MUST_FIX", result.getComments().get(0).getActionLevel());
    }

    @Test
    void parseFileReview_snakeCaseActionLevel_supported() {
        String rawOutput = """
                {
                  "filePath": "Action.java",
                  "summary": "Action",
                  "comments": [
                    {
                      "riskType": "STYLE",
                      "severity": "LOW",
                      "title": "Action",
                      "action_level": "optional"
                    }
                  ]
                }
                """;

        FileReviewResult result = parser.parseFileReview(rawOutput);

        assertEquals("OPTIONAL", result.getComments().get(0).getActionLevel());
    }

    @Test
    void parseFileReview_nonJson_throwsParseException() {
        String rawOutput = "This is not JSON at all";

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            parser.parseFileReview(rawOutput);
        });

        assertEquals(ErrorCode.AI_RESPONSE_PARSE_ERROR.getCode(), exception.getCode());
    }

    @Test
    void parseFileReview_emptyOutput_throwsException() {
        assertThrows(BusinessException.class, () -> {
            parser.parseFileReview("");
        });

        assertThrows(BusinessException.class, () -> {
            parser.parseFileReview(null);
        });
    }

    @Test
    void parseFileReview_partialJson_extractsObject() {
        String rawOutput = """
                Some text before
                {"filePath": "Partial.java", "summary": "Partial", "comments": []}
                Some text after
                """;

        FileReviewResult result = parser.parseFileReview(rawOutput);

        assertEquals("Partial.java", result.getFilePath());
    }
}
