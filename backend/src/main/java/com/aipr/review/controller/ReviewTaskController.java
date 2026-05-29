package com.aipr.review.controller;

import com.aipr.review.common.Result;
import com.aipr.review.dto.CreateReviewTaskRequest;
import com.aipr.review.service.report.ReviewReportService;
import com.aipr.review.service.review.ReviewTaskService;
import com.aipr.review.vo.ReviewCommentVO;
import com.aipr.review.vo.ReviewFileVO;
import com.aipr.review.vo.ReviewReportVO;
import com.aipr.review.vo.ReviewTaskCreatedVO;
import com.aipr.review.vo.ReviewTaskDetailVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/review-tasks")
public class ReviewTaskController {

    private final ReviewTaskService reviewTaskService;
    private final ReviewReportService reviewReportService;

    @PostMapping
    public Result<ReviewTaskCreatedVO> create(@Valid @RequestBody CreateReviewTaskRequest request) {
        return Result.ok(reviewTaskService.createTask(request.getPrUrl()));
    }

    @GetMapping("/{taskId}")
    public Result<ReviewTaskDetailVO> detail(@PathVariable Long taskId) {
        return Result.ok(reviewTaskService.getTask(taskId));
    }

    @GetMapping("/{taskId}/files")
    public Result<List<ReviewFileVO>> files(@PathVariable Long taskId) {
        return Result.ok(reviewTaskService.getFiles(taskId));
    }

    @GetMapping("/{taskId}/comments")
    public Result<List<ReviewCommentVO>> comments(@PathVariable Long taskId) {
        return Result.ok(reviewTaskService.getComments(taskId));
    }

    @GetMapping("/{taskId}/report")
    public Result<ReviewReportVO> report(@PathVariable Long taskId) {
        return Result.ok(reviewReportService.getReport(taskId));
    }
}
