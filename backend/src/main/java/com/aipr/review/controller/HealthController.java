package com.aipr.review.controller;

import com.aipr.review.common.Result;
import com.aipr.review.vo.HealthVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Result<HealthVO> health() {
        return Result.ok(HealthVO.builder()
                .status("UP")
                .service("ai-pr-review-backend")
                .build());
    }
}
