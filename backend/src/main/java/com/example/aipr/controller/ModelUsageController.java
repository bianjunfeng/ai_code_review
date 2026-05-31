package com.example.aipr.controller;

import com.example.aipr.common.Result;
import com.example.aipr.service.monitor.ModelUsageService;
import com.example.aipr.vo.ModelUsageDetailVO;
import com.example.aipr.vo.ModelUsageSummaryVO;
import com.example.aipr.vo.TaskModelUsageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/model-usage")
@RequiredArgsConstructor
public class ModelUsageController {

    private final ModelUsageService modelUsageService;

    @GetMapping("/summary")
    public Result<ModelUsageSummaryVO> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(7);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        return Result.ok(modelUsageService.getSummary(startDate, endDate));
    }

    @GetMapping("/logs")
    public Result<ModelUsageDetailVO> getLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) Boolean success) {
        return Result.ok(modelUsageService.getLogs(page, pageSize, taskId, success));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<TaskModelUsageVO> getTaskUsage(@PathVariable Long taskId) {
        return Result.ok(modelUsageService.getTaskUsage(taskId));
    }
}