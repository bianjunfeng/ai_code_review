package com.aipr.review.service.github;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GitHubChangedFile {

    private String filename;
    private String status;
    private Integer additions;
    private Integer deletions;
    private Integer changes;
    private String patch;
}
