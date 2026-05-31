package com.example.aipr.service.ai;

import com.example.aipr.common.BusinessException;
import com.example.aipr.dto.AiReviewContext;
import com.example.aipr.dto.FileReviewResult;
import com.example.aipr.enums.ErrorCode;
import com.example.aipr.service.prompt.AiReviewOutputParser;
import com.example.aipr.service.prompt.PromptRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiReviewServiceTest {

    private AiReviewService aiReviewService;
    private FakeLlmClient fakeLlmClient;

    @BeforeEach
    void setUp() {
        fakeLlmClient = new FakeLlmClient();
        aiReviewService = new AiReviewService(
                new PromptRenderer(),
                new AiReviewOutputParser(),
                fakeLlmClient,
                new ObjectMapper()
        );
    }

    @Test
    void reviewFile_validJson_returnsFileReviewResult() {
        String validJson = """
            {
              "filePath": "src/main/java/com/example/AuthService.java",
              "summary": "该文件修改了用户认证逻辑",
              "comments": [
                {
                  "line": 42,
                  "riskType": "BUG_RISK",
                  "severity": "HIGH",
                  "title": "空指针风险",
                  "description": "user对象可能为null",
                  "reason": "在user.getName()前未做null检查",
                  "suggestion": "添加 if (user != null) 检查",
                  "confidence": 0.85,
                  "needHumanCheck": false
                }
              ]
            }
            """;

        fakeLlmClient.setResponseContent(validJson);

        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .prDescription("Test description")
                .filePath("src/main/java/com/example/AuthService.java")
                .fileStatus("modified")
                .language("Java")
                .additions(30)
                .deletions(10)
                .patch("+ if (user != null) {\n+     System.out.println(user.getName());\n+ }")
                .build();

        FileReviewResult result = aiReviewService.reviewFile(context);

        assertNotNull(result);
        assertEquals("src/main/java/com/example/AuthService.java", result.getFilePath());
        assertEquals("该文件修改了用户认证逻辑", result.getSummary());
        assertEquals(1, result.getComments().size());
        assertEquals(42, result.getComments().get(0).getLine());
        assertEquals("BUG_RISK", result.getComments().get(0).getRiskType());
        assertEquals("HIGH", result.getComments().get(0).getSeverity());
        assertEquals(0.85, result.getComments().get(0).getConfidence());
    }

    @Test
    void reviewFile_markdownWrappedJson_parsesCorrectly() {
        String markdownJson = """
            以下是评审结果：

            ```json
            {
              "filePath": "src/utils/helper.js",
              "summary": "代码格式问题",
              "comments": [
                {
                  "line": 10,
                  "riskType": "STYLE",
                  "severity": "LOW",
                  "title": "缺少分号",
                  "description": "语句未以分号结尾",
                  "reason": "不符合团队代码规范",
                  "suggestion": "添加分号",
                  "confidence": 0.9,
                  "needHumanCheck": false
                }
              ]
            }
            ```

            以上仅供参考。
            """;

        fakeLlmClient.setResponseContent(markdownJson);

        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .filePath("src/utils/helper.js")
                .language("JavaScript")
                .patch("+ const a = 1\n+ const b = 2")
                .build();

        FileReviewResult result = aiReviewService.reviewFile(context);

        assertNotNull(result);
        assertEquals("代码格式问题", result.getSummary());
        assertEquals(1, result.getComments().size());
    }

    @Test
    void reviewFile_invalidJson_throwsBusinessException() {
        String invalidJson = "{ not valid json at all }";
        fakeLlmClient.setResponseContent(invalidJson);

        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .filePath("src/main/java/Test.java")
                .language("Java")
                .patch("+ System.out.println(\"test\");")
                .build();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> aiReviewService.reviewFile(context));

        assertEquals(ErrorCode.AI_RESPONSE_PARSE_ERROR.getCode(), exception.getCode());
    }

    @Test
    void reviewFile_pngFile_skipped() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .filePath("assets/logo.png")
                .patch("+ binary data")
                .build();

        FileReviewResult result = aiReviewService.reviewFile(context);

        assertNotNull(result);
        assertEquals("assets/logo.png", result.getFilePath());
        assertEquals("该文件类型暂不进行 AI Review", result.getSummary());
        assertTrue(result.getComments().isEmpty());
        assertEquals(0, fakeLlmClient.getCallCount());
    }

    @Test
    void reviewFile_jpgFile_skipped() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .filePath("images/photo.jpg")
                .patch("+ binary data")
                .build();

        FileReviewResult result = aiReviewService.reviewFile(context);

        assertNotNull(result);
        assertEquals("该文件类型暂不进行 AI Review", result.getSummary());
        assertEquals(0, fakeLlmClient.getCallCount());
    }

    @Test
    void reviewFile_svgFile_skipped() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .filePath("icons/icon.svg")
                .patch("<svg>...</svg>")
                .build();

        FileReviewResult result = aiReviewService.reviewFile(context);

        assertEquals("该文件类型暂不进行 AI Review", result.getSummary());
        assertEquals(0, fakeLlmClient.getCallCount());
    }

    @Test
    void reviewFile_packageLock_skipped() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .filePath("package-lock.json")
                .patch("+ \"version\": \"1.0.0\"")
                .build();

        FileReviewResult result = aiReviewService.reviewFile(context);

        assertEquals("该文件类型暂不进行 AI Review", result.getSummary());
        assertEquals(0, fakeLlmClient.getCallCount());
    }

    @Test
    void reviewFile_distDirectory_skipped() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .filePath("dist/index.js")
                .patch("+ compiled code")
                .build();

        FileReviewResult result = aiReviewService.reviewFile(context);

        assertEquals("该文件类型暂不进行 AI Review", result.getSummary());
        assertEquals(0, fakeLlmClient.getCallCount());
    }

    @Test
    void reviewFile_targetDirectory_skipped() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .filePath("target/classes/Main.class")
                .patch("+ bytecode")
                .build();

        FileReviewResult result = aiReviewService.reviewFile(context);

        assertEquals("该文件类型暂不进行 AI Review", result.getSummary());
        assertEquals(0, fakeLlmClient.getCallCount());
    }

    @Test
    void reviewFile_nodeModules_skipped() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .filePath("node_modules/express/index.js")
                .patch("+ some code")
                .build();

        FileReviewResult result = aiReviewService.reviewFile(context);

        assertEquals("该文件类型暂不进行 AI Review", result.getSummary());
        assertEquals(0, fakeLlmClient.getCallCount());
    }

    @Test
    void reviewFile_emptyPatch_skipped() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .filePath("src/main/java/Test.java")
                .language("Java")
                .patch("")
                .build();

        FileReviewResult result = aiReviewService.reviewFile(context);

        assertNotNull(result);
        assertEquals("src/main/java/Test.java", result.getFilePath());
        assertEquals("该文件无内容变更", result.getSummary());
        assertTrue(result.getComments().isEmpty());
        assertEquals(0, fakeLlmClient.getCallCount());
    }

    @Test
    void reviewFile_nullPatch_skipped() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .filePath("src/main/java/Test.java")
                .language("Java")
                .patch(null)
                .build();

        FileReviewResult result = aiReviewService.reviewFile(context);

        assertNotNull(result);
        assertEquals("该文件无内容变更", result.getSummary());
        assertEquals(0, fakeLlmClient.getCallCount());
    }

    @Test
    void reviewFile_nullFilePath_throwsException() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .filePath(null)
                .patch("+ some code")
                .build();

        FileReviewResult result = aiReviewService.reviewFile(context);

        assertNotNull(result);
        assertEquals("该文件类型暂不进行 AI Review", result.getSummary());
    }

    @Test
    void reviewFile_emptyFilePathWithEmptyModelResponse_throwsBusinessException() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .filePath("")
                .patch("+ some code")
                .build();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> aiReviewService.reviewFile(context));

        assertEquals(ErrorCode.AI_RESPONSE_PARSE_ERROR.getCode(), exception.getCode());
    }

    @Test
    void reviewFile_nullContext_handledSafely() {
        assertThrows(NullPointerException.class, () -> aiReviewService.reviewFile(null));
    }

    private static class FakeLlmClient implements LlmClient {
        private String responseContent = "";
        private int callCount = 0;

        public void setResponseContent(String content) {
            this.responseContent = content;
        }

        public int getCallCount() {
            return callCount;
        }

        @Override
        public LlmResponse chat(LlmRequest request) {
            callCount++;
            return LlmResponse.builder()
                    .content(responseContent)
                    .finishReason("stop")
                    .build();
        }

        @Override
        public LlmResponse chat(LlmRequest request, LlmCallContext context) {
            return chat(request);
        }
    }
}
