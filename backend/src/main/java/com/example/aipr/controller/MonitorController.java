package com.example.aipr.controller;

import com.example.aipr.common.Result;
import com.example.aipr.mapper.ReviewTaskMapper;
import com.example.aipr.vo.CacheStatisticsVO;
import com.example.aipr.vo.RecentFailuresVO;
import com.example.aipr.vo.ReviewTaskStatisticsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MonitorController {

    private final ReviewTaskMapper reviewTaskMapper;

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/review-tasks/statistics")
    public Result<ReviewTaskStatisticsVO> statistics() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        Long total = reviewTaskMapper.selectCount(null);
        Long success = defaultLong(reviewTaskMapper.countSuccess());
        Long failed = defaultLong(reviewTaskMapper.countFailed());
        Long running = defaultLong(reviewTaskMapper.countRunning());
        Long today = defaultLong(reviewTaskMapper.countToday(startOfDay));
        Long highRisk = defaultLong(reviewTaskMapper.countHighRisk());
        Long mediumRisk = defaultLong(reviewTaskMapper.countMediumRisk());
        Long lowRisk = defaultLong(reviewTaskMapper.countLowRisk());

        ReviewTaskStatisticsVO vo = ReviewTaskStatisticsVO.builder()
                .totalTasks(total)
                .todayTasks(today)
                .successTasks(success)
                .failedTasks(failed)
                .runningTasks(running)
                .highRiskTasks(highRisk)
                .mediumRiskTasks(mediumRisk)
                .lowRiskTasks(lowRisk)
                .avgDurationMs(0.0) // 暂不计算
                .build();

        return Result.ok(vo);
    }

    @GetMapping("/review-tasks/recent-failures")
    public Result<RecentFailuresVO> recentFailures(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int pageSize) {

        List<RecentFailuresVO.FailureRecord> records = reviewTaskMapper
                .findRecentFailures(pageSize)
                .stream()
                .map(task -> RecentFailuresVO.FailureRecord.builder()
                        .taskId(task.getId())
                        .prTitle(task.getPrTitle())
                        .status(task.getStatus())
                        .errorMessage(truncateError(task.getErrorMessage()))
                        .createdAt(task.getCreatedAt() != null
                                ? task.getCreatedAt().format(DATE_TIME_FORMATTER)
                                : null)
                        .build())
                .collect(Collectors.toList());

        return Result.ok(RecentFailuresVO.builder()
                .total((long) records.size())
                .records(records)
                .build());
    }

    @GetMapping("/review-cache/statistics")
    public Result<CacheStatisticsVO> cacheStatistics() {
        Long cacheHits = defaultLong(reviewTaskMapper.countCacheHits());
        Long cacheMisses = defaultLong(reviewTaskMapper.countCacheMisses());
        Long total = cacheHits + cacheMisses;
        double hitRate = total > 0 ? (double) cacheHits / total * 100 : 0.0;

        // 估算节省：每次缓存命中约节省 5000 tokens
        Long savedTokensEstimate = cacheHits * 5000L;
        Long savedModelCalls = cacheHits;

        CacheStatisticsVO vo = CacheStatisticsVO.builder()
                .cacheHits(cacheHits)
                .cacheMisses(cacheMisses)
                .cacheHitRate(Math.round(hitRate * 10) / 10.0)
                .savedModelCalls(savedModelCalls)
                .savedTokensEstimate(savedTokensEstimate)
                .build();

        return Result.ok(vo);
    }

    private Long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private String truncateError(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.length() > 500
                ? errorMessage.substring(0, 500)
                : errorMessage;
    }
}