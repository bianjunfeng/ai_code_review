package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GitHubPullRequestPageVO {

    private List<GitHubPullRequestVO> records;
    private Integer page;
    private Integer pageSize;
    private Long total;
    private Integer pages;
}
