package com.example.aipr.service.prompt;

import com.example.aipr.dto.AiReviewContext;
import com.example.aipr.dto.StaticRuleFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptRendererTest {

    private final PromptRenderer promptRenderer = new PromptRenderer();

    @Test
    void renderFileReviewPrompt_containsPrTitle() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("fix login token validation")
                .prDescription("add token refresh logic")
                .prAuthor("testuser")
                .sourceBranch("feature/login")
                .targetBranch("main")
                .filePath("src/main/java/AuthService.java")
                .fileStatus("modified")
                .language("Java")
                .additions(50)
                .deletions(10)
                .patch("+ some code\n- some old code")
                .build();

        String prompt = promptRenderer.renderFileReviewPrompt(context);

        assertTrue(prompt.contains("PR 标题：fix login token validation"));
        assertTrue(prompt.contains("PR 描述：add token refresh logic"));
        assertTrue(prompt.contains("文件路径：src/main/java/AuthService.java"));
        assertTrue(prompt.contains("+ some code"));
        assertTrue(prompt.contains("\"reason\""));
        assertTrue(prompt.contains("\"evidence\""));
        assertTrue(prompt.contains("\"actionLevel\""));
        assertTrue(prompt.contains("MUST_FIX|SHOULD_FIX|OPTIONAL"));
    }

    @Test
    void renderFileReviewPrompt_containsStaticRuleFindings() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .filePath("src/main/java/AuthService.java")
                .patch("+ private static final String API_KEY = \"sk-abcdefghijklmnopqrstuvwxyz\";")
                .staticRuleFindings(List.of(StaticRuleFinding.builder()
                        .ruleCode("HARD_CODED_SECRET")
                        .ruleName("疑似硬编码密钥")
                        .riskType("SECURITY_RISK")
                        .severity("HIGH")
                        .line(12)
                        .message("新增代码疑似直接写入 API Key。")
                        .evidence("+ private static final String API_KEY = \"sk-abcdefghijklmnopqrstuvwxyz\";")
                        .suggestion("请改为从环境变量读取。")
                        .build()))
                .build();

        String prompt = promptRenderer.renderFileReviewPrompt(context);

        assertTrue(prompt.contains("=== 静态规则扫描结果 ==="));
        assertTrue(prompt.contains("[HARD_CODED_SECRET] 疑似硬编码密钥"));
        assertTrue(prompt.contains("line: 12"));
        assertTrue(prompt.contains("只围绕 evidence 判断，不要泛化"));
        assertTrue(prompt.contains("必须基于 evidence 和 diff 判断是否成立"));
    }

    @Test
    void renderFileReviewPrompt_emptyFieldShowsEmpty() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .prDescription(null)
                .filePath("test.java")
                .patch("+ new line")
                .build();

        String prompt = promptRenderer.renderFileReviewPrompt(context);

        assertTrue(prompt.contains("PR 描述：未提供"));
    }

    @Test
    void renderFileReviewPrompt_truncatesLongPatch() {
        StringBuilder longPatch = new StringBuilder();
        for (int i = 0; i < 15000; i++) {
            longPatch.append("+ line ").append(i).append("\n");
        }

        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .filePath("test.java")
                .patch(longPatch.toString())
                .build();

        String prompt = promptRenderer.renderFileReviewPrompt(context);

        assertTrue(prompt.contains("[注意：diff 已截断"));
        assertTrue(context.getTruncated());
    }

    @Test
    void renderFileReviewPrompt_containsCommitSummary() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .prDescription("test desc")
                .prAuthor("testuser")
                .sourceBranch("feature/test")
                .targetBranch("main")
                .commitSummary("feat: add login; fix: correct validation logic; refactor: simplify token handling")
                .filePath("src/AuthService.java")
                .fileStatus("modified")
                .language("Java")
                .additions(20)
                .deletions(5)
                .patch("+ new code")
                .build();

        String prompt = promptRenderer.renderFileReviewPrompt(context);

        assertTrue(prompt.contains("PR 提交记录："));
        assertTrue(prompt.contains("feat: add login"));
        assertTrue(prompt.contains("fix: correct validation logic"));
        assertTrue(prompt.contains("refactor: simplify token handling"));
    }

    @Test
    void renderFileReviewPrompt_emptyCommitSummaryShowsNothing() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .prDescription("test desc")
                .sourceBranch("feature/test")
                .targetBranch("main")
                .commitSummary("")
                .filePath("src/AuthService.java")
                .fileStatus("modified")
                .language("Java")
                .patch("+ new code")
                .build();

        String prompt = promptRenderer.renderFileReviewPrompt(context);

        assertFalse(prompt.contains("PR 提交记录："));
    }

    @Test
    void renderFileReviewPrompt_nullCommitSummaryShowsNothing() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .sourceBranch("feature/test")
                .targetBranch("main")
                .commitSummary(null)
                .filePath("src/AuthService.java")
                .fileStatus("modified")
                .language("Java")
                .patch("+ new code")
                .build();

        String prompt = promptRenderer.renderFileReviewPrompt(context);

        assertFalse(prompt.contains("PR 提交记录："));
    }

    @Test
    void renderFileReviewPrompt_emptyPatchShowsNotice() {
        AiReviewContext context = AiReviewContext.builder()
                .prTitle("Test PR")
                .filePath("test.java")
                .patch("")
                .build();

        String prompt = promptRenderer.renderFileReviewPrompt(context);

        assertTrue(prompt.contains("该文件无内容变更"));
    }

    @Test
    void renderPrSummaryPrompt_containsStatsAndSummaries() {
        List<String> fileSummaries = List.of(
                "该文件新增登录认证逻辑。",
                "修改 UserService 校验逻辑。"
        );

        String prompt = promptRenderer.renderPrSummaryPrompt(
                "feat: add login", "testuser",
                "feature/login", "main",
                "feat: add login; fix: validation",
                fileSummaries,
                2, 0, 0, 0,
                1, 2, 3, 4);

        assertTrue(prompt.contains("PR 标题：feat: add login"));
        assertTrue(prompt.contains("PR 作者：testuser"));
        assertTrue(prompt.contains("PR 提交记录："));
        assertTrue(prompt.contains("feat: add login"));
        assertTrue(prompt.contains("该文件新增登录认证逻辑。"));
        assertTrue(prompt.contains("已分析文件数：2"));
        assertTrue(prompt.contains("CRITICAL：1"));
        assertTrue(prompt.contains("HIGH：2"));
        assertTrue(prompt.contains("LOW：4"));
        assertTrue(prompt.contains("\"summary\""));
        assertTrue(prompt.contains("\"finalReview\""));
        assertTrue(prompt.contains("\"testSuggestions\""));
    }

    @Test
    void renderPrSummaryPrompt_showsSkippedAndFailedWhenPresent() {
        String prompt = promptRenderer.renderPrSummaryPrompt(
                "Test PR", "author",
                "feature/test", "main",
                null,
                List.of("file1 summary"),
                5, 2, 1, 1,
                0, 1, 2, 3);

        assertTrue(prompt.contains("跳过文件数：2"));
        assertTrue(prompt.contains("失败文件数：1"));
        assertTrue(prompt.contains("截断文件数：1"));
    }

    @Test
    void renderPrSummaryPrompt_emptyFileSummariesShowsPlaceholder() {
        String prompt = promptRenderer.renderPrSummaryPrompt(
                "Test PR", "author",
                "feature/test", "main",
                null,
                List.of(),
                3, 0, 0, 0,
                0, 0, 0, 0);

        assertTrue(prompt.contains("（无文件级总结）"));
    }

    @Test
    void detectLanguage_javaFiles() {
        assertEquals("Java", promptRenderer.detectLanguage("Test.java"));
        assertEquals("Java", promptRenderer.detectLanguage("/path/to/AuthService.java"));
    }

    @Test
    void detectLanguage_vueFiles() {
        assertEquals("Vue", promptRenderer.detectLanguage("Component.vue"));
    }

    @Test
    void detectLanguage_jsAndTs() {
        assertEquals("JavaScript", promptRenderer.detectLanguage("app.js"));
        assertEquals("TypeScript", promptRenderer.detectLanguage("service.ts"));
    }

    @Test
    void detectLanguage_yamlAndJson() {
        assertEquals("YAML", promptRenderer.detectLanguage("application.yml"));
        assertEquals("YAML", promptRenderer.detectLanguage("config.yaml"));
        assertEquals("JSON", promptRenderer.detectLanguage("package.json"));
    }

    @Test
    void detectLanguage_unknownReturnsEmpty() {
        assertEquals("", promptRenderer.detectLanguage("README"));
        assertEquals("", promptRenderer.detectLanguage("Makefile"));
    }
}
