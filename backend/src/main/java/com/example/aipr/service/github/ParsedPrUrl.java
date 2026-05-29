package com.example.aipr.service.github;

public record ParsedPrUrl(String owner, String repo, Integer pullNumber) {
}
