package com.example.aipr.service.monitor;

import com.example.aipr.entity.ModelUsageLog;
import com.example.aipr.mapper.ModelUsageLogMapper;
import com.example.aipr.vo.ModelUsageDetailVO;
import com.example.aipr.vo.ModelUsageSummaryVO;
import com.example.aipr.vo.TaskModelUsageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelUsageService {

    private final ModelUsageLogMapper modelUsageLogMapper;

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ModelUsageSummaryVO getSummary(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<ModelUsageLog> logs = modelUsageLogMapper.findByDateRange(start, end);

        if (logs.isEmpty()) {
            return ModelUsageSummaryVO.builder()
                    .totalCalls(0L)
                    .successCalls(0L)
                    .failedCalls(0L)
                    .totalPromptTokens(0L)
                    .totalCompletionTokens(0L)
                    .totalTokens(0L)
                    .estimatedCost(0.0)
                    .avgLatencyMs(0.0)
                    .successRate(100.0)
                    .build();
        }

        long totalCalls = logs.size();
        long successCalls = logs.stream().filter(l -> Boolean.TRUE.equals(l.getSuccess())).count();
        long failedCalls = logs.stream().filter(l -> !Boolean.TRUE.equals(l.getSuccess())).count();
        long totalPromptTokens = logs.stream().mapToLong(l -> toLong(l.getPromptTokens())).sum();
        long totalCompletionTokens = logs.stream().mapToLong(l -> toLong(l.getCompletionTokens())).sum();
        long totalTokens = logs.stream().mapToLong(l -> toLong(l.getTotalTokens())).sum();
        double avgLatencyMs = logs.stream().mapToLong(l -> toLong(l.getLatencyMs())).average().orElse(0);
        double successRate = totalCalls > 0 ? (double) successCalls / totalCalls * 100 : 100.0;

        return ModelUsageSummaryVO.builder()
                .totalCalls(totalCalls)
                .successCalls(successCalls)
                .failedCalls(failedCalls)
                .totalPromptTokens(totalPromptTokens)
                .totalCompletionTokens(totalCompletionTokens)
                .totalTokens(totalTokens)
                .estimatedCost(calculateCost(totalTokens))
                .avgLatencyMs(avgLatencyMs)
                .successRate(Math.round(successRate * 10) / 10.0)
                .build();
    }

    public ModelUsageDetailVO getLogs(int page, int pageSize, Long taskId, Boolean success) {
        List<ModelUsageLog> logs;

        if (taskId != null) {
            logs = modelUsageLogMapper.findByTaskId(taskId);
        } else {
            LocalDateTime start = LocalDate.now().minusDays(7).atStartOfDay();
            LocalDateTime end = LocalDate.now().atTime(LocalTime.MAX);
            logs = modelUsageLogMapper.findByDateRange(start, end);
        }

        if (success != null) {
            logs = logs.stream()
                    .filter(l -> success.equals(l.getSuccess()))
                    .collect(Collectors.toList());
        }

        long total = logs.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, logs.size());

        // Protect against out-of-range page
        if (fromIndex >= logs.size()) {
            fromIndex = 0;
            toIndex = 0;
        }

        List<ModelUsageDetailVO.ModelUsageLogVO> records = logs.subList(fromIndex, toIndex)
                .stream()
                .map(this::toLogVO)
                .collect(Collectors.toList());

        return ModelUsageDetailVO.builder()
                .total(total)
                .records(records)
                .build();
    }

    public TaskModelUsageVO getTaskUsage(Long taskId) {
        List<ModelUsageLog> logs = modelUsageLogMapper.findByTaskId(taskId);

        if (logs.isEmpty()) {
            return TaskModelUsageVO.builder()
                    .taskId(taskId)
                    .totalCalls(0L)
                    .successCalls(0L)
                    .failedCalls(0L)
                    .totalPromptTokens(0L)
                    .totalCompletionTokens(0L)
                    .totalTokens(0L)
                    .avgLatencyMs(0.0)
                    .logs(List.of())
                    .build();
        }

        long totalCalls = logs.size();
        long successCalls = logs.stream().filter(l -> Boolean.TRUE.equals(l.getSuccess())).count();
        long failedCalls = logs.stream().filter(l -> !Boolean.TRUE.equals(l.getSuccess())).count();
        long totalPromptTokens = logs.stream().mapToLong(l -> toLong(l.getPromptTokens())).sum();
        long totalCompletionTokens = logs.stream().mapToLong(l -> toLong(l.getCompletionTokens())).sum();
        long totalTokens = logs.stream().mapToLong(l -> toLong(l.getTotalTokens())).sum();
        double avgLatencyMs = logs.stream().mapToLong(l -> toLong(l.getLatencyMs())).average().orElse(0);

        List<ModelUsageDetailVO.ModelUsageLogVO> logVOs = logs.stream()
                .map(this::toLogVO)
                .collect(Collectors.toList());

        return TaskModelUsageVO.builder()
                .taskId(taskId)
                .totalCalls(totalCalls)
                .successCalls(successCalls)
                .failedCalls(failedCalls)
                .totalPromptTokens(totalPromptTokens)
                .totalCompletionTokens(totalCompletionTokens)
                .totalTokens(totalTokens)
                .avgLatencyMs(avgLatencyMs)
                .logs(logVOs)
                .build();
    }

    public void recordUsage(ModelUsageLog usageLog) {
        try {
            modelUsageLogMapper.insert(usageLog);
            log.info("[ModelUsage] recorded usage: taskId={}, model={}, totalTokens={}, latencyMs={}, success={}",
                    usageLog.getTaskId(),
                    usageLog.getModelName(),
                    usageLog.getTotalTokens(),
                    usageLog.getLatencyMs(),
                    usageLog.getSuccess());
        } catch (Exception e) {
            log.warn("[ModelUsage] failed to record usage: {}", e.getMessage());
        }
    }

    private ModelUsageDetailVO.ModelUsageLogVO toLogVO(ModelUsageLog log) {
        return ModelUsageDetailVO.ModelUsageLogVO.builder()
                .id(log.getId())
                .taskId(log.getTaskId())
                .fileId(log.getFileId())
                .skillCode(log.getSkillCode())
                .provider(log.getProvider())
                .modelName(log.getModelName())
                .callType(log.getCallType())
                .promptTokens(log.getPromptTokens())
                .completionTokens(log.getCompletionTokens())
                .totalTokens(log.getTotalTokens())
                .latencyMs(log.getLatencyMs())
                .success(log.getSuccess())
                .errorMessage(log.getErrorMessage())
                .createdAt(log.getCreatedAt() != null ? log.getCreatedAt().format(DATE_TIME_FORMATTER) : null)
                .build();
    }

    private Long toLong(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private Double calculateCost(long totalTokens) {
        // DeepSeek pricing approximation: $0.27 per 1000 tokens (both input and output combined)
        // This is a rough estimate for demonstration
        return BigDecimal.valueOf(totalTokens)
                .multiply(BigDecimal.valueOf(0.27))
                .divide(BigDecimal.valueOf(1000), 4, BigDecimal.ROUND_HALF_UP)
                .doubleValue();
    }
}