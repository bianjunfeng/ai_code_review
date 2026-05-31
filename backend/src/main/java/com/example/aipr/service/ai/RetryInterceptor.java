package com.example.aipr.service.ai;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Response;

import java.io.IOException;

/**
 * 智能重试拦截器：只对可恢复的临时错误重试，避免无效重试浪费 Token。
 *
 * <p>重试策略：
 * <ul>
 *   <li>5xx（502/503/504）— 服务端临时故障，重试</li>
 *   <li>IOException（网络超时/连接断开）— 网络抖动，重试</li>
 *   <li>4xx（400/401/403/404）— 客户端或权限错误，不重试</li>
 *   <li>429（限流）— 返回给调用方决定，不盲目重试</li>
 * </ul>
 *
 * <p>对应文档：TODO-评审耗时优化设计方案 P1-6.4</p>
 */
@Slf4j
public class RetryInterceptor implements Interceptor {

    private final int maxRetries;

    public RetryInterceptor(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        IOException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            Response response = null;
            try {
                response = chain.proceed(chain.request());

                if (response.isSuccessful()) {
                    return response;
                }

                int code = response.code();

                // 4xx（除 429 外）：客户端错误，重试无意义
                if (code >= 400 && code < 500 && code != 429) {
                    log.debug("[Retry] HTTP {} 属于客户端错误，不重试", code);
                    return response;
                }

                // 429 限流：返回给调用方，不盲目重试
                if (code == 429) {
                    log.debug("[Retry] HTTP 429 限流，不重试");
                    return response;
                }

                // 5xx：服务端临时故障，重试
                if (attempt < maxRetries) {
                    response.close();
                    long delayMs = 1000L * (attempt + 1);
                    log.debug("[Retry] HTTP {} 重试 {}/{}, 等待 {}ms", code, attempt + 1, maxRetries, delayMs);
                    sleep(delayMs);
                } else {
                    return response;
                }

            } catch (IOException e) {
                // 网络层异常（超时/连接断开），重试
                lastException = e;
                if (attempt < maxRetries) {
                    long delayMs = 1000L * (attempt + 1);
                    log.debug("[Retry] IO异常重试 {}/{}, error={}, 等待 {}ms", attempt + 1, maxRetries, e.getMessage(), delayMs);
                    if (response != null) {
                        response.close();
                    }
                    sleep(delayMs);
                }
            }
        }

        throw lastException != null ? lastException : new IOException("重试耗尽");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
