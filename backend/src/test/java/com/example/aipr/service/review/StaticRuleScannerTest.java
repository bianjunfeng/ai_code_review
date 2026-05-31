package com.example.aipr.service.review;

import com.example.aipr.dto.StaticRuleFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StaticRuleScannerTest {

    private final StaticRuleScanner scanner = new StaticRuleScanner();

    @Test
    void scan_detectsHardCodedSecret() {
        String patch = """
                @@ -1,0 +10,3 @@
                +public class JwtConfig {
                +    private static final String API_KEY = "sk-abcdefghijklmnopqrstuvwxyz";
                +}
                """;

        List<StaticRuleFinding> findings = scanner.scan("src/main/java/JwtConfig.java", patch);

        StaticRuleFinding finding = findByRule(findings, "HARD_CODED_SECRET");
        assertNotNull(finding);
        assertEquals(11, finding.getLine());
        assertEquals("SECURITY_RISK", finding.getRiskType());
        assertEquals("HIGH", finding.getSeverity());
        assertTrue(finding.getEvidence().contains("API_KEY"));
    }

    @Test
    void scan_detectsSqlConcatenation() {
        String patch = """
                @@ -5,0 +5,2 @@
                +String sql = "select * from user where name = '" + username + "'";
                +jdbcTemplate.queryForList(sql);
                """;

        List<StaticRuleFinding> findings = scanner.scan("src/main/java/UserRepository.java", patch);

        StaticRuleFinding finding = findByRule(findings, "SQL_INJECTION_PATTERN");
        assertNotNull(finding);
        assertEquals(5, finding.getLine());
        assertTrue(finding.getMessage().contains("SQL"));
    }

    @Test
    void scan_detectsMyBatisRawParameter() {
        String patch = """
                @@ -20,0 +20,1 @@
                +    order by ${sortField}
                """;

        List<StaticRuleFinding> findings = scanner.scan("src/main/resources/mapper/UserMapper.xml", patch);

        StaticRuleFinding finding = findByRule(findings, "SQL_INJECTION_PATTERN");
        assertNotNull(finding);
        assertEquals(20, finding.getLine());
        assertTrue(finding.getEvidence().contains("${sortField}"));
    }

    @Test
    void scan_detectsSystemOutPrintln() {
        String patch = """
                @@ -3,0 +3,1 @@
                +System.out.println("login success");
                """;

        List<StaticRuleFinding> findings = scanner.scan("src/main/java/AuthService.java", patch);

        StaticRuleFinding finding = findByRule(findings, "SYSTEM_OUT_PRINTLN");
        assertNotNull(finding);
        assertEquals("MAINTAINABILITY", finding.getRiskType());
        assertEquals("LOW", finding.getSeverity());
    }

    @Test
    void scan_detectsConsoleLog() {
        String patch = """
                @@ -8,0 +8,1 @@
                +console.log("submit", form);
                """;

        List<StaticRuleFinding> findings = scanner.scan("frontend/src/views/HomeView.vue", patch);

        StaticRuleFinding finding = findByRule(findings, "CONSOLE_LOG");
        assertNotNull(finding);
        assertEquals(8, finding.getLine());
    }

    @Test
    void scan_detectsEmptyCatch() {
        String patch = """
                @@ -30,0 +30,4 @@
                +try {
                +    callRemote();
                +} catch (Exception e) {
                +}
                """;

        List<StaticRuleFinding> findings = scanner.scan("src/main/java/RemoteService.java", patch);

        StaticRuleFinding finding = findByRule(findings, "EMPTY_CATCH");
        assertNotNull(finding);
        assertEquals(32, finding.getLine());
        assertEquals("BUG_RISK", finding.getRiskType());
        assertEquals("MEDIUM", finding.getSeverity());
    }

    private StaticRuleFinding findByRule(List<StaticRuleFinding> findings, String ruleCode) {
        return findings.stream()
                .filter(finding -> ruleCode.equals(finding.getRuleCode()))
                .findFirst()
                .orElse(null);
    }
}
