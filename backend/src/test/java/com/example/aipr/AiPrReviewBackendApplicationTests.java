package com.example.aipr;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class AiPrReviewBackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void healthReturnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void githubPreviewParsesPrUrl() throws Exception {
        mockMvc.perform(post("/api/github/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prUrl\":\"https://github.com/owner/repo/pull/12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.owner").value("owner"))
                .andExpect(jsonPath("$.data.repo").value("repo"))
                .andExpect(jsonPath("$.data.pullNumber").value(12));
    }

    @Test
    void githubPreviewRejectsInvalidPrUrl() throws Exception {
        mockMvc.perform(post("/api/github/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prUrl\":\"https://github.com/owner/repo/issues/12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("PR 链接格式错误，请输入 GitHub Pull Request 地址"));
    }

    @Test
    void createReviewTaskReturnsPendingStatus() throws Exception {
        mockMvc.perform(post("/api/review-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prUrl\":\"https://github.com/owner/repo/pull/12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void getReviewReportReturnsMockReport() throws Exception {
        mockMvc.perform(get("/api/review-tasks/1/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(1))
                .andExpect(jsonPath("$.data.riskItems[0].riskType").value("SECURITY_RISK"));
    }
}
