package com.example.aipr.controller;

import com.example.aipr.common.Result;
import com.example.aipr.vo.HealthVO;
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
