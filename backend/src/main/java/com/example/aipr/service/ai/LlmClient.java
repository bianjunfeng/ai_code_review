package com.example.aipr.service.ai;

public interface LlmClient {
    LlmResponse chat(LlmRequest request);
}