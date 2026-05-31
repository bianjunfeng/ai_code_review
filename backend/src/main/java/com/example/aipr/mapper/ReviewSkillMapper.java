package com.example.aipr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aipr.entity.ReviewSkill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReviewSkillMapper extends BaseMapper<ReviewSkill> {

    List<ReviewSkill> findAllEnabled();

    List<ReviewSkill> findByLanguage(@Param("language") String language);

    ReviewSkill findBySkillCode(@Param("skillCode") String skillCode);
}
