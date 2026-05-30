package com.example.aipr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aipr.entity.ReviewSkillResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReviewSkillResultMapper extends BaseMapper<ReviewSkillResult> {

    @Select("SELECT * FROM review_skill_result WHERE task_id = #{taskId} ORDER BY created_at ASC")
    List<ReviewSkillResult> findByTaskId(@Param("taskId") Long taskId);

    @Select("SELECT * FROM review_skill_result WHERE task_id = #{taskId} AND skill_name = #{skillName}")
    ReviewSkillResult findByTaskIdAndSkillName(@Param("taskId") Long taskId, @Param("skillName") String skillName);
}