package com.example.aipr.controller;

import com.example.aipr.common.Result;
import com.example.aipr.dto.CreateReviewTaskRequest;
import com.example.aipr.service.report.ReviewReportService;
import com.example.aipr.service.review.ReviewTaskService;
import com.example.aipr.vo.ReviewCommentVO;
import com.example.aipr.vo.ReviewFileVO;
import com.example.aipr.vo.ReviewReportVO;
import com.example.aipr.vo.ReviewTaskCreatedVO;
import com.example.aipr.vo.ReviewTaskDetailVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
        return Result.ok(reviewTaskService.createTask(request.getPrUrl(), request.getForceRefresh()));
    }

    @GetMapping("/{taskId}")
    public Result<ReviewTaskDetailVO> detail(@PathVariable Long taskId) {
        return Result.ok(reviewTaskService.getTask(taskId));
    }

    @GetMapping("/{taskId}/files")
    public Result<List<ReviewFileVO>> files(@PathVariable Long taskId) {
        return Result.ok(reviewTaskService.listFiles(taskId));
    }

    @GetMapping("/{taskId}/comments")
    public Result<List<ReviewCommentVO>> comments(@PathVariable Long taskId,
                                                  @RequestParam(required = false) String riskLevel,
                                                  @RequestParam(required = false) String riskType) {
        return Result.ok(reviewTaskService.listComments(taskId, riskLevel, riskType));
    }

    @GetMapping("/{taskId}/report")
    public Result<ReviewReportVO> report(@PathVariable Long taskId) {
        return Result.ok(reviewReportService.getReport(taskId));
    }
}
