package com.example.aipr.util;

import jakarta.servlet.http.HttpServletRequest;

public class IpUtils {

    private IpUtils() {
    }

    public static String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (isValidIp(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (isValidIp(realIp)) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    private static boolean isValidIp(String value) {
        return value != null
                && !value.isBlank()
                && !"unknown".equalsIgnoreCase(value);
    }
}