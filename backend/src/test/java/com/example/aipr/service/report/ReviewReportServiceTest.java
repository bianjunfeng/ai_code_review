package com.example.aipr.service.report;

import com.example.aipr.entity.ReviewComment;
import com.example.aipr.entity.ReviewFile;
import com.example.aipr.entity.ReviewTask;
import com.example.aipr.mapper.ReviewCommentMapper;
import com.example.aipr.mapper.ReviewFileMapper;
import com.example.aipr.service.review.ReviewTaskService;
import com.example.aipr.vo.ReviewMarkdownVO;
import com.example.aipr.vo.ReviewReportVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewReportServiceTest {

    @Mock
    private ReviewTaskService reviewTaskService;

    @Mock
    private ReviewFileMapper reviewFileMapper;

    @Mock
    private ReviewCommentMapper reviewCommentMapper;

    @InjectMocks
    private ReviewReportService reviewReportService;

    @Test
    void getReviewMarkdown_returnsStableMarkdown() {
        Long taskId = 12L;
        when(reviewTaskService.requireTask(taskId)).thenReturn(task());
        when(reviewFileMapper.findByTaskId(taskId)).thenReturn(List.of(file()));
        when(reviewCommentMapper.findByTaskId(taskId)).thenReturn(List.of(comment()));

        ReviewMarkdownVO result = reviewReportService.getReviewMarkdown(taskId);

        assertEquals(taskId, result.getTaskId());
        assertTrue(result.getMarkdown().contains("## AI Review 总结"));
        assertTrue(result.getMarkdown().contains("风险等级：HIGH"));
        assertTrue(result.getMarkdown().contains("PR：补充评审接口"));
        assertTrue(result.getMarkdown().contains("#### 1. [HIGH] 空指针风险"));
        assertTrue(result.getMarkdown().contains("依据：缺少空值保护"));
        assertTrue(result.getMarkdown().contains("证据：user.getName()"));
        assertTrue(result.getMarkdown().contains("处理级别：MUST_FIX"));
        assertTrue(result.getMarkdown().contains("### 最终结论"));
    }

    @Test
    void getReport_partialSuccess_returnsReportNotExecutingMessage() {
        Long taskId = 12L;
        when(reviewTaskService.requireTask(taskId)).thenReturn(partialSuccessTask());
        when(reviewFileMapper.findByTaskId(taskId)).thenReturn(List.of(analyzedFile(), skippedFile(), failedFile()));
        when(reviewCommentMapper.findByTaskId(taskId)).thenReturn(List.of(comment()));

        ReviewReportVO result = reviewReportService.getReport(taskId);

        assertEquals(3, result.getTotalFileCount());
        assertEquals(1, result.getAnalyzedFileCount());
        assertEquals(1, result.getSkippedFileCount());
        assertEquals(1, result.getTruncatedFileCount());
        assertEquals(1, result.getFailedFileCount());
        assertTrue(result.getSummary().contains("部分文件分析失败"));
    }

    @Test
    void getReviewMarkdown_partialSuccess_includesFileStatsAndFailureNotice() {
        Long taskId = 12L;
        when(reviewTaskService.requireTask(taskId)).thenReturn(partialSuccessTask());
        when(reviewFileMapper.findByTaskId(taskId)).thenReturn(List.of(analyzedFile(), skippedFile(), failedFile()));
        when(reviewCommentMapper.findByTaskId(taskId)).thenReturn(List.of(comment()));

        ReviewMarkdownVO result = reviewReportService.getReviewMarkdown(taskId);

        assertTrue(result.getMarkdown().contains("文件统计：总数 3，已分析 1，跳过 1，截断 1，失败 1"));
        assertTrue(result.getMarkdown().contains("注意：本次任务有 1 个文件分析失败"));
    }

    @Test
    void getReport_allFilesSuccess_noFailedFileCount() {
        Long taskId = 12L;
        when(reviewTaskService.requireTask(taskId)).thenReturn(task());
        when(reviewFileMapper.findByTaskId(taskId)).thenReturn(List.of(file()));
        when(reviewCommentMapper.findByTaskId(taskId)).thenReturn(List.of(comment()));

        ReviewReportVO result = reviewReportService.getReport(taskId);

        assertEquals(1, result.getTotalFileCount());
        assertEquals(1, result.getAnalyzedFileCount());
        assertEquals(0, result.getSkippedFileCount());
        assertEquals(0, result.getTruncatedFileCount());
        assertEquals(0, result.getFailedFileCount());
    }

    @Test
    void getReviewMarkdown_allSuccess_noFailureNotice() {
        Long taskId = 12L;
        when(reviewTaskService.requireTask(taskId)).thenReturn(task());
        when(reviewFileMapper.findByTaskId(taskId)).thenReturn(List.of(file()));
        when(reviewCommentMapper.findByTaskId(taskId)).thenReturn(List.of(comment()));

        ReviewMarkdownVO result = reviewReportService.getReviewMarkdown(taskId);

        assertTrue(result.getMarkdown().contains("失败 0"));
        assertFalse(result.getMarkdown().contains("注意：本次任务有"));
    }

    private ReviewTask task() {
        ReviewTask task = new ReviewTask();
        task.setPrTitle("补充评审接口");
        task.setPrAuthor("crash73");
        task.setPrUrl("https://github.com/crash73/test-for-PRreview/pull/1");
        task.setSourceBranch("feature/a");
        task.setTargetBranch("dev");
        task.setStatus("SUCCESS");
        task.setRiskScore(80);
        task.setRiskLevel("HIGH");
        task.setSummary("本次 PR 补充 Markdown 导出能力。");
        task.setFinalReview("建议修复风险后合并。");
        return task;
    }

    private ReviewFile file() {
        ReviewFile file = new ReviewFile();
        file.setFilePath("src/main/java/Demo.java");
        file.setFileStatus("modified");
        file.setAdditions(10);
        file.setDeletions(2);
        file.setAiSummary("新增 Review Markdown 导出接口。");
        return file;
    }

    private ReviewComment comment() {
        ReviewComment comment = new ReviewComment();
        comment.setFilePath("src/main/java/Demo.java");
        comment.setLineNumber(42);
        comment.setRiskLevel("HIGH");
        comment.setRiskType("BUG_RISK");
        comment.setTitle("空指针风险");
        comment.setDescription("调用对象前没有判断为空。");
        comment.setReason("缺少空值保护");
        comment.setEvidence("user.getName()");
        comment.setActionLevel("MUST_FIX");
        comment.setSuggestion("调用前增加非空校验。");
        comment.setConfidence(BigDecimal.valueOf(0.91));
        comment.setNeedHumanCheck(true);
        return comment;
    }

    private ReviewTask partialSuccessTask() {
        ReviewTask task = new ReviewTask();
        task.setPrTitle("补充评审接口");
        task.setPrAuthor("crash73");
        task.setPrUrl("https://github.com/crash73/test-for-PRreview/pull/1");
        task.setSourceBranch("feature/a");
        task.setTargetBranch("dev");
        task.setStatus("PARTIAL_SUCCESS");
        task.setRiskScore(65);
        task.setRiskLevel("MEDIUM");
        task.setSummary("部分文件分析失败：1 个文件未生成 AI Review 结果");
        task.setFinalReview("注意：有 1 个文件分析失败，合并前需要人工补充检查。建议处理主要中风险问题，并结合人工复核后合并。");
        task.setErrorMessage("部分文件分析失败：1 个文件未生成 AI Review 结果");
        return task;
    }

    private ReviewFile analyzedFile() {
        ReviewFile file = new ReviewFile();
        file.setFilePath("src/main/java/Demo.java");
        file.setFileStatus("modified");
        file.setAdditions(10);
        file.setDeletions(2);
        file.setSkipped(false);
        file.setTruncated(true);
        file.setAiSummary("新增 Review Markdown 导出接口。");
        return file;
    }

    private ReviewFile skippedFile() {
        ReviewFile file = new ReviewFile();
        file.setFilePath("package-lock.json");
        file.setFileStatus("modified");
        file.setAdditions(100);
        file.setDeletions(50);
        file.setSkipped(true);
        file.setSkipReason("锁文件，跳过分析");
        file.setAiSummary(null);
        return file;
    }

    private ReviewFile failedFile() {
        ReviewFile file = new ReviewFile();
        file.setFilePath("src/main/java/Broken.java");
        file.setFileStatus("added");
        file.setAdditions(20);
        file.setDeletions(0);
        file.setSkipped(false);
        file.setAiSummary("[分析失败] 超时或异常：AI 调用超时");
        return file;
    }
}
