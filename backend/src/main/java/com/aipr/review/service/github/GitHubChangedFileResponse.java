package com.aipr.review.service.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubChangedFileResponse {

    private String filename;
    private String status;
    private Integer additions;
    private Integer deletions;
    private Integer changes;
    private String patch;
}
