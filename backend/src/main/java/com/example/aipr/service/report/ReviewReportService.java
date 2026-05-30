package com.example.aipr.service.report;

import com.example.aipr.enums.RiskType;
import com.example.aipr.enums.Severity;
import com.example.aipr.vo.PrInfoVO;
import com.example.aipr.vo.ReviewReportVO;
import com.example.aipr.vo.RiskItemVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewReportService {

    public ReviewReportVO getReport(Long taskId) {
        return ReviewReportVO.builder()
                .taskId(taskId)
                .prInfo(PrInfoVO.builder()
                        .title("fix login token validation")
                        .author("demo-user")
                        .url("https://github.com/owner/repo/pull/12")
                        .sourceBranch("feature/login")
                        .targetBranch("main")
                        .changedFiles(3)
                        .additions(120)
                        .deletions(30)
                        .build())
                .summary("本次 PR 主要修改了登录认证逻辑，涉及 token 生成和用户登录接口。")
                .riskScore(78)
                .riskLevel(Severity.HIGH.name())
                .mainChanges(List.of(
                        "新增 JwtUtil 工具类",
                        "修改 LoginService 登录逻辑",
                        "调整 UserController 返回结构"
                ))
                .riskItems(List.of(
                        RiskItemVO.builder()
                                .filePath("src/main/java/com/demo/auth/JwtUtil.java")
                                .line(null)
                                .riskLevel(Severity.HIGH.name())
                                .riskType(RiskType.SECURITY_RISK.name())
                                .title("JWT 密钥存在硬编码风险")
                                .description("当前代码将 JWT 密钥写在源码中，容易造成密钥泄露。")
                                .suggestion("建议将密钥改为从环境变量或配置中心读取，并避免在日志中输出。")
                                .confidence(0.92)
                                .needHumanCheck(true)
                                .build()
                ))
                .testSuggestions(List.of(
                        "建议补充 token 过期场景测试",
                        "建议补充登录失败场景测试"
                ))
                .finalReview("建议修改高风险问题后再合并。")
                .build();
    }
}
