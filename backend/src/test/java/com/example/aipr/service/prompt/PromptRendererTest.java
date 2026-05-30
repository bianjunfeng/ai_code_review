package com.example.aipr.service.prompt;

import com.example.aipr.dto.AiReviewContext;
import org.junit.jupiter.api.Test;

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