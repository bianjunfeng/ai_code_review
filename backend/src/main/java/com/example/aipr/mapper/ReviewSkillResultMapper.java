package com.example.aipr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aipr.entity.ReviewSkillResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReviewSkillResultMapper extends BaseMapper<ReviewSkillResult> {

    List<ReviewSkillResult> findByTaskId(@Param("taskId") Long taskId);

    ReviewSkillResult findByTaskIdAndSkillName(@Param("taskId") Long taskId, @Param("skillName") String skillName);
}
