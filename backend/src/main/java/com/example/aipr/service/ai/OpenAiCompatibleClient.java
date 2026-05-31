package com.example.aipr.service.ai;

import com.example.aipr.common.BusinessException;
import com.example.aipr.config.AiProperties;
import com.example.aipr.entity.ModelUsageLog;
import com.example.aipr.enums.ErrorCode;
import com.example.aipr.service.monitor.ModelUsageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class OpenAiCompatibleClient implements LlmClient {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final ModelUsageService modelUsageService;

    public OpenAiCompatibleClient(AiProperties aiProperties, ObjectMapper objectMapper, ModelUsageService modelUsageService) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.modelUsageService = modelUsageService;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new RetryInterceptor(1))
                .build();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        return chat(request, null);
    }

    @Override
    public LlmResponse chat(LlmRequest request, LlmCallContext context) {
        String apiKey = aiProperties.getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务未配置，请联系管理员");
        }

        String baseUrl = aiProperties.getBaseUrl();
        if (baseUrl == null) {
            baseUrl = "https://api.deepseek.com/";
        } else if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }
        String url = baseUrl + "chat/completions";

        String modelName = request.getModel() != null ? request.getModel() : aiProperties.getModelName();
        log.debug("Calling AI service with model: {}", modelName);

        long startTime = System.currentTimeMillis();
        ModelUsageLog usageLog = buildUsageLog(context, modelName);

        try {
            String requestBody = buildRequestBody(request);

            Request httpRequest = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                LlmResponse llmResponse = parseResponseWithUsage(response, usageLog, startTime);
                recordUsage(usageLog, true, null);
                return llmResponse;
            }
        } catch (BusinessException e) {
            recordUsage(usageLog, false, e.getMessage());
            throw e;
        } catch (IOException e) {
            log.error("AI service call failed: {}", e.getMessage());
            recordUsage(usageLog, false, e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务调用失败，请稍后重试");
        }
    }

    private ModelUsageLog buildUsageLog(LlmCallContext context, String modelName) {
        ModelUsageLog log = new ModelUsageLog();
        if (context != null) {
            log.setTaskId(context.getTaskId());
            log.setFileId(context.getFileId());
            log.setSkillCode(context.getSkillCode());
            log.setCallType(context.getCallType());
        }
        log.setModelName(modelName);
        log.setProvider("deepseek");
        log.setSuccess(true);
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }

    private void recordUsage(ModelUsageLog usageLog, boolean success, String errorMessage) {
        try {
            usageLog.setSuccess(success);
            usageLog.setErrorMessage(truncateError(errorMessage));
            modelUsageService.recordUsage(usageLog);
        } catch (Exception e) {
            log.warn("[ModelUsage] failed to record usage: {}", e.getMessage());
        }
    }

    private String truncateError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private String buildRequestBody(LlmRequest request) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
            root.put("model", request.getModel() != null ? request.getModel() : aiProperties.getModelName());

            if (request.getMessages() != null) {
                var messagesArray = root.putArray("messages");
                for (LlmMessage message : request.getMessages()) {
                    var msgNode = messagesArray.addObject();
                    msgNode.put("role", message.getRole());
                    msgNode.put("content", message.getContent());
                }
            }

            if (request.getTemperature() != null) {
                root.put("temperature", request.getTemperature());
            } else {
                root.put("temperature", aiProperties.getTemperature());
            }

            if (request.getMaxTokens() != null) {
                root.put("max_tokens", request.getMaxTokens());
            } else {
                root.put("max_tokens", aiProperties.getMaxTokens());
            }

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "构建请求失败");
        }
    }

    private LlmResponse parseResponseWithUsage(Response response, ModelUsageLog usageLog, long startTime) throws IOException {
        long latencyMs = System.currentTimeMillis() - startTime;
        usageLog.setLatencyMs(latencyMs);

        if (!response.isSuccessful()) {
            log.error("AI service returned error status: {}", response.code());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务调用失败，请稍后重试");
        }

        String body = response.body() != null ? response.body().string() : "";
        if (body.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务返回为空");
        }

        try {
            JsonNode root = objectMapper.readTree(body);

            // Parse usage data
            JsonNode usage = root.get("usage");
            if (usage != null) {
                if (usage.get("prompt_tokens") != null) {
                    usageLog.setPromptTokens(usage.get("prompt_tokens").asInt());
                }
                if (usage.get("completion_tokens") != null) {
                    usageLog.setCompletionTokens(usage.get("completion_tokens").asInt());
                }
                if (usage.get("total_tokens") != null) {
                    usageLog.setTotalTokens(usage.get("total_tokens").asInt());
                }
            }

            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务返回格式异常");
            }

            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            if (message == null) {
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务返回格式异常");
            }

            String content = message.get("content") != null ? message.get("content").asText() : "";
            String finishReason = firstChoice.get("finish_reason") != null ? firstChoice.get("finish_reason").asText() : null;

            // Calculate estimated cost
            if (usageLog.getTotalTokens() != null && usageLog.getTotalTokens() > 0) {
                usageLog.setEstimatedCost(BigDecimal.valueOf(usageLog.getTotalTokens())
                        .multiply(BigDecimal.valueOf(0.27))
                        .divide(BigDecimal.valueOf(1000), 4, BigDecimal.ROUND_HALF_UP));
            }

            return LlmResponse.builder()
                    .content(content)
                    .finishReason(finishReason)
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务返回格式异常");
        }
    }
}