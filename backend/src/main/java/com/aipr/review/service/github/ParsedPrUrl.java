package com.aipr.review.service.github;

public record ParsedPrUrl(String owner, String repo, Integer pullNumber) {
}
