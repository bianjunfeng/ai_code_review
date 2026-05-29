package com.aipr.review.service.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubPullRequestResponse {

    private String title;
    private String body;
    private GitHubUser user;
    private GitHubRef head;
    private GitHubRef base;
    private String state;
    private Integer additions;
    private Integer deletions;

    @JsonProperty("changed_files")
    private Integer changedFiles;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitHubUser {
        private String login;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitHubRef {
        private String ref;
    }
}
