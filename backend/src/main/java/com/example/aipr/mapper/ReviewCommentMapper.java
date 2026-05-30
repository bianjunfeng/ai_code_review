package com.example.aipr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aipr.entity.ReviewComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReviewCommentMapper extends BaseMapper<ReviewComment> {

    @Select("SELECT * FROM review_comment WHERE task_id = #{taskId} ORDER BY risk_level DESC, created_at ASC")
    List<ReviewComment> findByTaskId(@Param("taskId") Long taskId);

    @Select("SELECT * FROM review_comment WHERE task_id = #{taskId} AND risk_level = #{riskLevel} ORDER BY created_at ASC")
    List<ReviewComment> findByTaskIdAndRiskLevel(@Param("taskId") Long taskId, @Param("riskLevel") String riskLevel);

    @Select("SELECT COUNT(*) FROM review_comment WHERE task_id = #{taskId}")
    int countByTaskId(@Param("taskId") Long taskId);

    @Select("SELECT COUNT(*) FROM review_comment WHERE task_id = #{taskId} AND risk_level = 'HIGH'")
    int countHighRiskByTaskId(@Param("taskId") Long taskId);
}