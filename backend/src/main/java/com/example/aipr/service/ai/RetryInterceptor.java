package com.example.aipr.service.ai;

import okhttp3.Interceptor;
import okhttp3.Response;

import java.io.IOException;

public class RetryInterceptor implements Interceptor {

    private final int maxRetries;

    public RetryInterceptor(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = null;
        IOException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (response != null && !response.isSuccessful()) {
                    response.close();
                }
                response = chain.proceed(chain.request());

                if (response.isSuccessful() || attempt == maxRetries) {
                    return response;
                }

                if (response.code() >= 500 && attempt < maxRetries) {
                    response.close();
                    try {
                        Thread.sleep(1000L * (attempt + 1));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Retry interrupted", e);
                    }
                }
            } catch (IOException e) {
                lastException = e;
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000L * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Retry interrupted", ie);
                    }
                }
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        return response;
    }
}