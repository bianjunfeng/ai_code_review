package com.example.aipr.controller;

import com.example.aipr.common.Result;
import com.example.aipr.dto.CreateReviewTaskRequest;
import com.example.aipr.service.report.ReviewReportService;
import com.example.aipr.service.review.ReviewTaskService;
import com.example.aipr.vo.ReviewReportVO;
import com.example.aipr.vo.ReviewTaskCreatedVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/{taskId}/report")
    public Result<ReviewReportVO> report(@PathVariable Long taskId) {
        return Result.ok(reviewReportService.getReport(taskId));
    }
}
