package com.example.aipr.service.report;

import com.example.aipr.entity.ReviewComment;
import com.example.aipr.entity.ReviewFile;
import com.example.aipr.entity.ReviewTask;
import com.example.aipr.mapper.ReviewCommentMapper;
import com.example.aipr.mapper.ReviewFileMapper;
import com.example.aipr.service.review.ReviewTaskService;
import com.example.aipr.vo.ReviewMarkdownVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(result.getMarkdown().contains("原因：缺少空值保护"));
        assertTrue(result.getMarkdown().contains("### 最终结论"));
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
        comment.setSuggestion("调用前增加非空校验。");
        comment.setConfidence(BigDecimal.valueOf(0.91));
        comment.setNeedHumanCheck(true);
        return comment;
    }
}
