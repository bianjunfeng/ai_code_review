package com.example.aipr.service.ai;

import com.example.aipr.common.BusinessException;
import com.example.aipr.config.AiProperties;
import com.example.aipr.enums.ErrorCode;
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
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class OpenAiCompatibleClient implements LlmClient {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public OpenAiCompatibleClient(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new RetryInterceptor(3))
                .build();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
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

        log.debug("Calling AI service with model: {}", request.getModel() != null ? request.getModel() : aiProperties.getModelName());

        try {
            String requestBody = buildRequestBody(request);

            Request httpRequest = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                return parseResponse(response);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("AI service call failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务调用失败，请稍后重试");
        }
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

    private LlmResponse parseResponse(Response response) throws IOException {
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