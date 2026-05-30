package com.example.aipr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aipr.entity.ReviewComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReviewCommentMapper extends BaseMapper<ReviewComment> {

    List<ReviewComment> findByTaskId(@Param("taskId") Long taskId);

    List<ReviewComment> findByTaskIdAndRiskLevel(@Param("taskId") Long taskId, @Param("riskLevel") String riskLevel);

    int countByTaskId(@Param("taskId") Long taskId);

    int countHighRiskByTaskId(@Param("taskId") Long taskId);

    void insertBatch(@Param("list") List<ReviewComment> comments);
}
