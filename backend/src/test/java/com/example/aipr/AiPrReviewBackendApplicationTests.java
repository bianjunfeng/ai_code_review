package com.example.aipr;

import com.example.aipr.service.github.GitHubChangedFile;
import com.example.aipr.service.github.GitHubClient;
import com.example.aipr.service.github.GitHubPrInfo;
import com.example.aipr.service.review.ReviewTaskExecutor;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
class AiPrReviewBackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GitHubClient gitHubClient;

    @MockBean
    private ReviewTaskExecutor reviewTaskExecutor;

    @BeforeEach
    void setUp() {
        when(gitHubClient.getPullRequest(any())).thenReturn(GitHubPrInfo.builder()
                .owner("owner")
                .repo("repo")
                .pullNumber(12)
                .title("feat: add login api")
                .author("demo-user")
                .sourceBranch("feature/login")
                .targetBranch("main")
                .state("OPEN")
                .additions(20)
                .deletions(5)
                .changedFiles(1)
                .build());
        when(gitHubClient.getPullRequestFiles(any())).thenReturn(List.of(GitHubChangedFile.builder()
                .filename("src/main/java/com/demo/LoginService.java")
                .status("modified")
                .additions(20)
                .deletions(5)
                .changes(25)
                .patch("@@ -1,1 +1,1 @@")
                .build()));
        doNothing().when(reviewTaskExecutor).executeAsync(any(Long.class));
    }

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
                .andExpect(jsonPath("$.data.pullNumber").value(12))
                .andExpect(jsonPath("$.data.title").value("feat: add login api"))
                .andExpect(jsonPath("$.data.files[0].filename").value("src/main/java/com/demo/LoginService.java"));
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
    void getReviewTaskDetailReturnsPersistedTask() throws Exception {
        Long taskId = createTaskAndReturnId();

        mockMvc.perform(get("/api/review-tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.prTitle").value("feat: add login api"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void getReviewFilesReturnsPersistedFiles() throws Exception {
        Long taskId = createTaskAndReturnId();

        mockMvc.perform(get("/api/review-tasks/" + taskId + "/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].filePath").value("src/main/java/com/demo/LoginService.java"));
    }

    @Test
    void getReviewReportReturnsPersistedTaskReport() throws Exception {
        Long taskId = createTaskAndReturnId();

        mockMvc.perform(get("/api/review-tasks/" + taskId + "/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.prInfo.title").value("feat: add login api"))
                .andExpect(jsonPath("$.data.riskItems").isArray());
    }

    private Long createTaskAndReturnId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/review-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prUrl\":\"https://github.com/owner/repo/pull/12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Integer taskId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.taskId");
        return taskId.longValue();
    }
}
