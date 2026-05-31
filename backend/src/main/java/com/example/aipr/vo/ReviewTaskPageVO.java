package com.example.aipr.vo;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ReviewTaskPageVO {

    private List<ReviewTaskListVO> records;
    private Long total;
    private Integer page;
    private Integer pageSize;
    private Integer pages;
}