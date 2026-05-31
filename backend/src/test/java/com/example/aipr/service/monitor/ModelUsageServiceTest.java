package com.example.aipr.service.monitor;

import com.example.aipr.entity.ModelUsageLog;
import com.example.aipr.mapper.ModelUsageLogMapper;
import com.example.aipr.vo.ModelUsageDetailVO;
import com.example.aipr.vo.ModelUsageSummaryVO;
import com.example.aipr.vo.TaskModelUsageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModelUsageServiceTest {

    @Autowired
    private ModelUsageService modelUsageService;

    @Autowired
    private ModelUsageLogMapper modelUsageLogMapper;

    @BeforeEach
    void cleanup() {
        modelUsageLogMapper.delete(null);
    }

    @Test
    void getSummary_emptyDb_returnsZeroStats() {
        ModelUsageSummaryVO result = modelUsageService.getSummary(LocalDate.now(), LocalDate.now());

        assertNotNull(result);
        assertEquals(0L, result.getTotalCalls());
        assertEquals(0L, result.getSuccessCalls());
        assertEquals(0L, result.getFailedCalls());
        assertEquals(100.0, result.getSuccessRate());
    }

    @Test
    void recordUsage_success_writeLog() {
        ModelUsageLog log = createUsageLog(1L, 2L, true);
        modelUsageService.recordUsage(log);

        ModelUsageSummaryVO summary = modelUsageService.getSummary(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        assertEquals(1L, summary.getTotalCalls());
        assertEquals(1L, summary.getSuccessCalls());
        assertEquals(0L, summary.getFailedCalls());
    }

    @Test
    void recordUsage_failure_writeLogWithError() {
        ModelUsageLog log = createUsageLog(3L, 4L, false);
        log.setErrorMessage("Connection timeout");
        modelUsageService.recordUsage(log);

        ModelUsageSummaryVO summary = modelUsageService.getSummary(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        assertEquals(1L, summary.getTotalCalls());
        assertEquals(0L, summary.getSuccessCalls());
        assertEquals(1L, summary.getFailedCalls());
    }

    @Test
    void getTaskUsage_withLogs_returnsStats() {
        Long taskId = 100L;

        ModelUsageLog log1 = createUsageLog(taskId, 10L, true);
        log1.setPromptTokens(500);
        log1.setCompletionTokens(100);
        log1.setTotalTokens(600);
        modelUsageService.recordUsage(log1);

        ModelUsageLog log2 = createUsageLog(taskId, 11L, true);
        log2.setPromptTokens(300);
        log2.setCompletionTokens(50);
        log2.setTotalTokens(350);
        modelUsageService.recordUsage(log2);

        TaskModelUsageVO result = modelUsageService.getTaskUsage(taskId);
        assertNotNull(result);
        assertEquals(taskId, result.getTaskId());
        assertEquals(2L, result.getTotalCalls());
        assertEquals(2L, result.getSuccessCalls());
        assertEquals(0L, result.getFailedCalls());
        assertEquals(800L, result.getTotalPromptTokens());
        assertEquals(150L, result.getTotalCompletionTokens());
        assertEquals(950L, result.getTotalTokens());
    }

    @Test
    void getTaskUsage_noLogs_returnsEmptyResult() {
        TaskModelUsageVO result = modelUsageService.getTaskUsage(99999L);
        assertNotNull(result);
        assertEquals(99999L, result.getTaskId());
        assertEquals(0L, result.getTotalCalls());
    }

    @Test
    void getLogs_withPagination_works() {
        Long taskId = 200L;
        for (int i = 0; i < 5; i++) {
            ModelUsageLog log = createUsageLog(taskId, (long) (100 + i), true);
            modelUsageService.recordUsage(log);
        }

        ModelUsageDetailVO page1 = modelUsageService.getLogs(1, 2, null, null);
        assertNotNull(page1);
        assertTrue(page1.getTotal() >= 5);
        assertEquals(2, page1.getRecords().size());

        ModelUsageDetailVO page2 = modelUsageService.getLogs(2, 2, null, null);
        assertNotNull(page2);
        assertEquals(2, page2.getRecords().size());
    }

    @Test
    void getLogs_filterBySuccess_works() {
        Long taskId = 300L;
        modelUsageService.recordUsage(createUsageLog(taskId, 301L, true));
        modelUsageService.recordUsage(createUsageLog(taskId, 302L, false));

        ModelUsageDetailVO successOnly = modelUsageService.getLogs(1, 10, taskId, true);
        assertEquals(1, successOnly.getRecords().size());
        assertTrue(successOnly.getRecords().get(0).getSuccess());

        ModelUsageDetailVO failedOnly = modelUsageService.getLogs(1, 10, taskId, false);
        assertEquals(1, failedOnly.getRecords().size());
        assertFalse(failedOnly.getRecords().get(0).getSuccess());
    }

    private ModelUsageLog createUsageLog(Long taskId, Long fileId, boolean success) {
        ModelUsageLog log = new ModelUsageLog();
        log.setTaskId(taskId);
        log.setFileId(fileId);
        log.setSkillCode(null);
        log.setProvider("test-provider");
        log.setModelName("test-model");
        log.setCallType("FILE_REVIEW");
        log.setPromptTokens(100);
        log.setCompletionTokens(50);
        log.setTotalTokens(150);
        log.setLatencyMs(1000L);
        log.setSuccess(success);
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }
}