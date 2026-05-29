package com.aipr.review;

import com.aipr.review.common.BusinessException;
import com.aipr.review.enums.ErrorCode;
import com.aipr.review.service.github.GitHubChangedFileResponse;
import com.aipr.review.service.github.GitHubClient;
import com.aipr.review.service.github.GitHubPullRequestResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AiPrReviewBackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubClient gitHubClient;

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
        mockGitHubPreview();

        mockMvc.perform(post("/api/github/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prUrl\":\"https://github.com/owner/repo/pull/12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.owner").value("owner"))
                .andExpect(jsonPath("$.data.repo").value("repo"))
                .andExpect(jsonPath("$.data.pullNumber").value(12))
                .andExpect(jsonPath("$.data.title").value("feat: add login api"))
                .andExpect(jsonPath("$.data.author").value("octocat"))
                .andExpect(jsonPath("$.data.sourceBranch").value("feature/login"))
                .andExpect(jsonPath("$.data.targetBranch").value("main"))
                .andExpect(jsonPath("$.data.files[0].filename").value("src/main/java/UserService.java"))
                .andExpect(jsonPath("$.data.files[0].patch").value("@@ -1,1 +1,2 @@"));
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
    void githubPreviewHandlesInvalidToken() throws Exception {
        when(gitHubClient.getPullRequest(any()))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_TOKEN_INVALID));

        mockMvc.perform(post("/api/github/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prUrl\":\"https://github.com/owner/repo/pull/12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("GitHub Token 无效，请检查 GITHUB_TOKEN"));
    }

    @Test
    void createReviewTaskFetchesPrAndReturnsReviewingStatus() throws Exception {
        mockGitHubPreview();

        mockMvc.perform(post("/api/review-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prUrl\":\"https://github.com/owner/repo/pull/12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(1))
                .andExpect(jsonPath("$.data.status").value("REVIEWING"));
    }

    @Test
    void reviewTaskDetailAndFilesComeFromFetchedPr() throws Exception {
        mockGitHubPreview();

        mockMvc.perform(post("/api/review-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prUrl\":\"https://github.com/owner/repo/pull/12\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/review-tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.prTitle").value("feat: add login api"))
                .andExpect(jsonPath("$.data.prAuthor").value("octocat"))
                .andExpect(jsonPath("$.data.status").value("REVIEWING"));

        mockMvc.perform(get("/api/review-tasks/1/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].filePath").value("src/main/java/UserService.java"))
                .andExpect(jsonPath("$.data[0].language").value("Java"))
                .andExpect(jsonPath("$.data[0].skipped").value(false));
    }

    @Test
    void getReviewReportAggregatesTaskAndFiles() throws Exception {
        mockGitHubPreview();

        mockMvc.perform(post("/api/review-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prUrl\":\"https://github.com/owner/repo/pull/12\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/review-tasks/1/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(1))
                .andExpect(jsonPath("$.data.status").value("REVIEWING"))
                .andExpect(jsonPath("$.data.prInfo.title").value("feat: add login api"))
                .andExpect(jsonPath("$.data.files[0].filePath").value("src/main/java/UserService.java"))
                .andExpect(jsonPath("$.data.riskItems.length()").value(0));
    }

    @Test
    void missingReviewTaskReturnsFriendlyError() throws Exception {
        mockMvc.perform(get("/api/review-tasks/999/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40402))
                .andExpect(jsonPath("$.message").value("Review 任务不存在"));
    }

    private void mockGitHubPreview() {
        GitHubPullRequestResponse pullRequest = new GitHubPullRequestResponse();
        pullRequest.setTitle("feat: add login api");
        pullRequest.setBody("Add login endpoint");
        pullRequest.setState("open");
        pullRequest.setAdditions(20);
        pullRequest.setDeletions(5);
        pullRequest.setChangedFiles(1);

        GitHubPullRequestResponse.GitHubUser user = new GitHubPullRequestResponse.GitHubUser();
        user.setLogin("octocat");
        pullRequest.setUser(user);

        GitHubPullRequestResponse.GitHubRef head = new GitHubPullRequestResponse.GitHubRef();
        head.setRef("feature/login");
        pullRequest.setHead(head);

        GitHubPullRequestResponse.GitHubRef base = new GitHubPullRequestResponse.GitHubRef();
        base.setRef("main");
        pullRequest.setBase(base);

        GitHubChangedFileResponse file = new GitHubChangedFileResponse();
        file.setFilename("src/main/java/UserService.java");
        file.setStatus("modified");
        file.setAdditions(20);
        file.setDeletions(5);
        file.setChanges(25);
        file.setPatch("@@ -1,1 +1,2 @@");

        when(gitHubClient.getPullRequest(any())).thenReturn(pullRequest);
        when(gitHubClient.listChangedFiles(any())).thenReturn(List.of(file));
    }
}
