package com.example.aipr;

import com.example.aipr.service.github.GitHubChangedFile;
import com.example.aipr.service.github.GitHubClient;
import com.example.aipr.service.github.GitHubPrInfo;
import com.example.aipr.entity.ReviewComment;
import com.example.aipr.entity.ReviewFile;
import com.example.aipr.entity.ReviewTask;
import com.example.aipr.mapper.ReviewCommentMapper;
import com.example.aipr.mapper.ReviewFileMapper;
import com.example.aipr.mapper.ReviewTaskMapper;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Autowired
    private ReviewTaskMapper reviewTaskMapper;

    @Autowired
    private ReviewFileMapper reviewFileMapper;

    @Autowired
    private ReviewCommentMapper reviewCommentMapper;

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

    @Test
    void createReviewTaskWithCacheHitCopiesFilesAndComments() throws Exception {
        String prUrl = "https://github.com/cache-owner/cache-repo/pull/99";
        when(gitHubClient.getPullRequest(any())).thenReturn(GitHubPrInfo.builder()
                .owner("cache-owner")
                .repo("cache-repo")
                .pullNumber(99)
                .title("fix: cached report")
                .author("cache-user")
                .sourceBranch("feature/cache")
                .targetBranch("main")
                .headSha("cache-head-1")
                .baseSha("cache-base-1")
                .build());
        when(gitHubClient.getPullRequestFiles(any())).thenReturn(List.of(GitHubChangedFile.builder()
                .filename("src/main/java/com/demo/CacheService.java")
                .status("modified")
                .additions(8)
                .deletions(1)
                .changes(9)
                .patch("@@ -1,1 +1,1 @@ cached")
                .build()));

        Long sourceTaskId = createTaskAndReturnId(prUrl);
        ReviewFile sourceFile = reviewFileMapper.findByTaskId(sourceTaskId).get(0);
        sourceFile.setAiSummary("缓存文件总结");
        reviewFileMapper.updateById(sourceFile);

        ReviewComment comment = new ReviewComment();
        comment.setTaskId(sourceTaskId);
        comment.setFilePath(sourceFile.getFilePath());
        comment.setLineNumber(12);
        comment.setRiskType("BUG_RISK");
        comment.setRiskLevel("HIGH");
        comment.setTitle("缓存风险项");
        comment.setDescription("缓存命中后也应该展示评论。");
        comment.setReason("文件和评论需要完整复制到新任务。");
        comment.setEvidence("copyCacheHitDetails");
        comment.setActionLevel("SHOULD_FIX");
        comment.setSuggestion("复制 review_file 和 review_comment。");
        comment.setConfidence(BigDecimal.valueOf(0.88));
        comment.setNeedHumanCheck(true);
        comment.setCreatedAt(LocalDateTime.now());
        reviewCommentMapper.insert(comment);

        ReviewTask success = new ReviewTask();
        success.setId(sourceTaskId);
        success.setStatus("SUCCESS");
        success.setRiskScore(70);
        success.setRiskLevel("HIGH");
        success.setSummary("缓存源报告总结");
        success.setFinalReview("缓存源最终结论");
        success.setUpdatedAt(LocalDateTime.now());
        reviewTaskMapper.updateById(success);

        MvcResult cachedResult = mockMvc.perform(post("/api/review-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prUrl\":\"" + prUrl + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.cached").value(true))
                .andExpect(jsonPath("$.data.cachedFromTaskId").value(sourceTaskId))
                .andReturn();

        Integer cachedTaskId = JsonPath.read(cachedResult.getResponse().getContentAsString(), "$.data.taskId");

        mockMvc.perform(get("/api/review-tasks/" + cachedTaskId + "/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].filePath").value("src/main/java/com/demo/CacheService.java"))
                .andExpect(jsonPath("$.data[0].aiSummary").value("缓存文件总结"));

        mockMvc.perform(get("/api/review-tasks/" + cachedTaskId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("缓存风险项"));

        mockMvc.perform(get("/api/review-tasks/" + cachedTaskId + "/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("缓存源报告总结"))
                .andExpect(jsonPath("$.data.riskItems[0].title").value("缓存风险项"));
    }

    @Test
    void createReviewTaskTruncatesOversizedPatchWhenSavingFiles() throws Exception {
        String prUrl = "https://github.com/large-owner/large-repo/pull/100";
        String largePatch = "x".repeat(12001);
        when(gitHubClient.getPullRequest(any())).thenReturn(GitHubPrInfo.builder()
                .owner("large-owner")
                .repo("large-repo")
                .pullNumber(100)
                .title("feat: large patch")
                .author("large-user")
                .sourceBranch("feature/large")
                .targetBranch("main")
                .build());
        when(gitHubClient.getPullRequestFiles(any())).thenReturn(List.of(GitHubChangedFile.builder()
                .filename("src/main/java/com/demo/LargeService.java")
                .status("modified")
                .additions(200)
                .deletions(10)
                .changes(210)
                .patch(largePatch)
                .build()));

        Long taskId = createTaskAndReturnId(prUrl);

        mockMvc.perform(get("/api/review-tasks/" + taskId + "/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].originalPatchLength").value(12001))
                .andExpect(jsonPath("$.data[0].analyzedPatchLength").value(12000))
                .andExpect(jsonPath("$.data[0].truncated").value(true));
    }

    private Long createTaskAndReturnId() throws Exception {
        return createTaskAndReturnId("https://github.com/owner/repo/pull/12");
    }

    private Long createTaskAndReturnId(String prUrl) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/review-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prUrl\":\"" + prUrl + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Integer taskId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.taskId");
        return taskId.longValue();
    }
}
