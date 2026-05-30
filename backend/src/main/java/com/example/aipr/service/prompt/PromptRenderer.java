package com.example.aipr.service.prompt;

import com.example.aipr.dto.AiReviewContext;
import org.springframework.stereotype.Component;

@Component
public class PromptRenderer {

    private static final int MAX_PATCH_LENGTH = 12000;

    public String renderFileReviewPrompt(AiReviewContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的代码评审助手。请分析以下 GitHub Pull Request 的文件变更，给出安全、质量和可维护性方面的评审意见。\n\n");

        if (context.getPrTitle() != null && !context.getPrTitle().isEmpty()) {
            prompt.append("PR 标题：").append(context.getPrTitle()).append("\n");
        }
        if (context.getPrDescription() != null && !context.getPrDescription().isEmpty()) {
            prompt.append("PR 描述：").append(context.getPrDescription()).append("\n");
        } else {
            prompt.append("PR 描述：未提供\n");
        }
        if (context.getPrAuthor() != null && !context.getPrAuthor().isEmpty()) {
            prompt.append("PR 作者：").append(context.getPrAuthor()).append("\n");
        }

        prompt.append("源分支：").append(context.getSourceBranch() != null ? context.getSourceBranch() : "未指定").append("\n");
        prompt.append("目标分支：").append(context.getTargetBranch() != null ? context.getTargetBranch() : "未指定").append("\n");

        prompt.append("\n=== 待评审文件 ===\n");
        prompt.append("文件路径：").append(context.getFilePath() != null ? context.getFilePath() : "").append("\n");
        prompt.append("文件状态：").append(context.getFileStatus() != null ? context.getFileStatus() : "").append("\n");
        prompt.append("文件语言：").append(context.getLanguage() != null ? context.getLanguage() : "").append("\n");
        prompt.append("变更行数：+").append(context.getAdditions() != null ? context.getAdditions() : 0);
        prompt.append(" -").append(context.getDeletions() != null ? context.getDeletions() : 0).append("\n\n");

        String patch = context.getPatch();
        if (patch == null || patch.isEmpty()) {
            prompt.append("该文件无内容变更或无法获取变更内容。\n");
        } else {
            boolean truncated = patch.length() > MAX_PATCH_LENGTH;
            context.setTruncated(truncated);
            if (truncated) {
                patch = patch.substring(0, MAX_PATCH_LENGTH);
                prompt.append("代码变更 (diff)：\n").append(patch).append("\n");
                prompt.append("\n[注意：diff 已截断，超出长度限制]\n");
            } else {
                prompt.append("代码变更 (diff)：\n").append(patch).append("\n");
            }
        }

        prompt.append("\n请基于上述信息，对该文件进行代码评审。只输出合法 JSON，不要输出 Markdown 或其他内容。\n");
        prompt.append("输出格式：\n");
        prompt.append("{\n");
        prompt.append("  \"filePath\": \"文件路径（与输入一致）\",\n");
        prompt.append("  \"summary\": \"该文件变更的整体总结\",\n");
        prompt.append("  \"comments\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"line\": null 或行号,\n");
        prompt.append("      \"riskType\": \"BUG_RISK|SECURITY_RISK|PERFORMANCE_RISK|MAINTAINABILITY|STYLE|TEST_RISK|COMPATIBILITY\",\n");
        prompt.append("      \"riskLevel\": \"CRITICAL|HIGH|MEDIUM|LOW|INFO\",\n");
        prompt.append("      \"title\": \"风险项标题\",\n");
        prompt.append("      \"description\": \"详细描述\",\n");
        prompt.append("      \"suggestion\": \"建议\",\n");
        prompt.append("      \"confidence\": 0.0-1.0 之间的数字,\n");
        prompt.append("      \"needHumanCheck\": true 或 false\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        prompt.append("评审要求：\n");
        prompt.append("1. 只基于提供的 PR 信息和 diff 分析，不要编造未提供的业务背景。\n");
        prompt.append("2. 不确定时设置 needHumanCheck=true。\n");
        prompt.append("3. 每条建议必须包含 confidence。\n");
        prompt.append("4. 不要输出泛泛而谈的建议，要具体指出问题所在。\n");
        prompt.append("5. 如果没有问题，comments 可以为空数组。\n");
        prompt.append("6. 只输出合法 JSON，不要输出 Markdown 代码块。\n");

        return prompt.toString();
    }

    public String detectLanguage(String filePath) {
        if (filePath == null) {
            return "";
        }
        if (filePath.endsWith(".java")) {
            return "Java";
        } else if (filePath.endsWith(".vue")) {
            return "Vue";
        } else if (filePath.endsWith(".js")) {
            return "JavaScript";
        } else if (filePath.endsWith(".ts")) {
            return "TypeScript";
        } else if (filePath.endsWith(".xml")) {
            return "XML";
        } else if (filePath.endsWith(".yml") || filePath.endsWith(".yaml")) {
            return "YAML";
        } else if (filePath.endsWith(".json")) {
            return "JSON";
        } else if (filePath.endsWith(".sql")) {
            return "SQL";
        } else if (filePath.endsWith(".md")) {
            return "Markdown";
        } else if (filePath.endsWith(".py")) {
            return "Python";
        } else if (filePath.endsWith(".go")) {
            return "Go";
        } else if (filePath.endsWith(".rs")) {
            return "Rust";
        } else if (filePath.endsWith(".cpp") || filePath.endsWith(".cc") || filePath.endsWith(".cxx")) {
            return "C++";
        } else if (filePath.endsWith(".c") || filePath.endsWith(".h")) {
            return "C";
        }
        return "";
    }
}
