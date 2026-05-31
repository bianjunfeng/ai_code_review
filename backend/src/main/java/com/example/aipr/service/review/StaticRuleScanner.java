package com.example.aipr.service.review;

import com.example.aipr.dto.StaticRuleFinding;
import com.example.aipr.enums.RiskType;
import com.example.aipr.enums.Severity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StaticRuleScanner {

    private static final int MAX_EVIDENCE_LENGTH = 160;

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*$");
    private static final Pattern HARD_CODED_SECRET = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key|client[_-]?secret)[A-Z0-9_\\-.]*\\s*[:=]\\s*[\"'][^\"']{8,}[\"']");
    private static final Pattern SECRET_LITERAL = Pattern.compile(
            "(?i)[\"'](sk-[A-Za-z0-9_\\-]{8,}|ghp_[A-Za-z0-9_]{8,}|AKIA[0-9A-Z]{12,}|AIza[0-9A-Za-z_\\-]{10,})[\"']");
    private static final Pattern MYBATIS_RAW_PARAMETER = Pattern.compile("\\$\\{[^}]+}");
    private static final Pattern SQL_KEYWORD = Pattern.compile("(?i)\\b(select|insert|update|delete|from|where)\\b");
    private static final Pattern SYSTEM_OUT_PRINTLN = Pattern.compile("\\bSystem\\.out\\.println\\s*\\(");
    private static final Pattern CONSOLE_LOG = Pattern.compile("(^|[^\\w.])console\\.log\\s*\\(");
    private static final Pattern CATCH_INLINE_EMPTY = Pattern.compile(".*\\bcatch\\s*\\([^)]*\\)\\s*\\{\\s*(?://.*)?}.*");
    private static final Pattern CATCH_START = Pattern.compile(".*\\bcatch\\s*\\([^)]*\\)\\s*\\{\\s*(?://.*)?$");

    public List<StaticRuleFinding> scan(String filePath, String patch) {
        if (patch == null || patch.isBlank()) {
            return Collections.emptyList();
        }

        List<PatchLine> addedLines = parseAddedLines(patch);
        if (addedLines.isEmpty()) {
            return Collections.emptyList();
        }

        List<StaticRuleFinding> findings = new ArrayList<>();
        for (int i = 0; i < addedLines.size(); i++) {
            PatchLine line = addedLines.get(i);
            scanLine(filePath, line, findings);
            scanEmptyCatch(filePath, addedLines, i, findings);
        }
        return findings;
    }

    private void scanLine(String filePath, PatchLine line, List<StaticRuleFinding> findings) {
        String content = line.content();
        if (containsHardCodedSecret(content)) {
            findings.add(buildFinding(
                    filePath,
                    line.lineNumber(),
                    "HARD_CODED_SECRET",
                    "疑似硬编码密钥",
                    RiskType.SECURITY_RISK.name(),
                    Severity.HIGH.name(),
                    "新增代码疑似直接写入 token、secret、password 或 API Key。",
                    content,
                    "请改为从环境变量或安全配置读取，并避免提交真实密钥。"
            ));
        }

        if (containsSqlRisk(content)) {
            findings.add(buildFinding(
                    filePath,
                    line.lineNumber(),
                    "SQL_INJECTION_PATTERN",
                    "SQL 拼接或 MyBatis 原始替换",
                    RiskType.SECURITY_RISK.name(),
                    Severity.HIGH.name(),
                    "新增代码疑似使用 SQL 字符串拼接或 MyBatis ${} 原始替换。",
                    content,
                    "请优先使用参数化查询、#{ } 绑定参数或白名单字段映射。"
            ));
        }

        if (SYSTEM_OUT_PRINTLN.matcher(content).find()) {
            findings.add(buildFinding(
                    filePath,
                    line.lineNumber(),
                    "SYSTEM_OUT_PRINTLN",
                    "System.out.println 调试输出",
                    RiskType.MAINTAINABILITY.name(),
                    Severity.LOW.name(),
                    "新增代码包含 System.out.println，可能是临时调试输出。",
                    content,
                    "请改用规范日志并确认不会输出敏感信息。"
            ));
        }

        if (CONSOLE_LOG.matcher(content).find()) {
            findings.add(buildFinding(
                    filePath,
                    line.lineNumber(),
                    "CONSOLE_LOG",
                    "console.log 调试输出",
                    RiskType.MAINTAINABILITY.name(),
                    Severity.LOW.name(),
                    "新增前端代码包含 console.log，可能是临时调试输出。",
                    content,
                    "请删除临时调试输出，必要日志应通过项目统一方式处理。"
            ));
        }
    }

    private boolean containsHardCodedSecret(String content) {
        return HARD_CODED_SECRET.matcher(content).find() || SECRET_LITERAL.matcher(content).find();
    }

    private boolean containsSqlRisk(String content) {
        if (MYBATIS_RAW_PARAMETER.matcher(content).find()) {
            return true;
        }
        if (!SQL_KEYWORD.matcher(content).find()) {
            return false;
        }
        return content.contains("\" +")
                || content.contains("' +")
                || content.matches(".*\\+\\s*[A-Za-z_][A-Za-z0-9_.]*.*");
    }

    private void scanEmptyCatch(String filePath,
                                List<PatchLine> addedLines,
                                int index,
                                List<StaticRuleFinding> findings) {
        PatchLine line = addedLines.get(index);
        String content = line.content().trim();
        if (CATCH_INLINE_EMPTY.matcher(content).matches()) {
            findings.add(emptyCatchFinding(filePath, line));
            return;
        }

        if (!CATCH_START.matcher(content).matches()) {
            return;
        }

        boolean hasStatement = false;
        for (int i = index + 1; i < addedLines.size(); i++) {
            String next = stripInlineComment(addedLines.get(i).content()).trim();
            if (next.isBlank() || next.startsWith("//") || next.startsWith("/*") || next.startsWith("*")) {
                continue;
            }
            if (next.startsWith("}")) {
                if (!hasStatement) {
                    findings.add(emptyCatchFinding(filePath, line));
                }
                return;
            }
            hasStatement = true;
        }
    }

    private StaticRuleFinding emptyCatchFinding(String filePath, PatchLine line) {
        return buildFinding(
                filePath,
                line.lineNumber(),
                "EMPTY_CATCH",
                "空 catch 块",
                RiskType.BUG_RISK.name(),
                Severity.MEDIUM.name(),
                "新增代码疑似吞掉异常，可能导致问题被隐藏。",
                line.content(),
                "请至少记录规范日志，或按业务语义抛出/转换异常。"
        );
    }

    private List<PatchLine> parseAddedLines(String patch) {
        List<PatchLine> lines = new ArrayList<>();
        Integer newLineNumber = null;

        for (String rawLine : patch.split("\\R", -1)) {
            Matcher hunkMatcher = HUNK_HEADER.matcher(rawLine);
            if (hunkMatcher.matches()) {
                newLineNumber = Integer.parseInt(hunkMatcher.group(1));
                continue;
            }

            if (rawLine.startsWith("+++") || rawLine.startsWith("---") || rawLine.startsWith("\\ No newline")) {
                continue;
            }

            if (rawLine.startsWith("+")) {
                lines.add(new PatchLine(newLineNumber, rawLine.substring(1)));
                if (newLineNumber != null) {
                    newLineNumber++;
                }
                continue;
            }

            if (rawLine.startsWith("-")) {
                continue;
            }

            if (newLineNumber != null && rawLine.startsWith(" ")) {
                newLineNumber++;
            }
        }

        return lines;
    }

    private StaticRuleFinding buildFinding(String filePath,
                                           Integer line,
                                           String ruleCode,
                                           String ruleName,
                                           String riskType,
                                           String severity,
                                           String message,
                                           String evidence,
                                           String suggestion) {
        return StaticRuleFinding.builder()
                .filePath(filePath)
                .line(line)
                .ruleCode(ruleCode)
                .ruleName(ruleName)
                .riskType(riskType)
                .severity(severity)
                .message(message)
                .evidence(toEvidence(evidence))
                .suggestion(suggestion)
                .build();
    }

    private String toEvidence(String content) {
        String evidence = "+ " + (content == null ? "" : content.trim());
        if (evidence.length() <= MAX_EVIDENCE_LENGTH) {
            return evidence;
        }
        return evidence.substring(0, MAX_EVIDENCE_LENGTH) + "...";
    }

    private String stripInlineComment(String content) {
        int commentIndex = content.indexOf("//");
        if (commentIndex < 0) {
            return content;
        }
        return content.substring(0, commentIndex);
    }

    private record PatchLine(Integer lineNumber, String content) {
    }
}
