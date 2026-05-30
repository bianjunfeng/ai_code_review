package com.example.aipr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aipr.entity.ReviewSkill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReviewSkillMapper extends BaseMapper<ReviewSkill> {

    @Select("SELECT * FROM review_skill WHERE enabled = 1 ORDER BY priority ASC")
    List<ReviewSkill> findAllEnabled();

    @Select("SELECT * FROM review_skill WHERE supported_languages LIKE CONCAT('%', #{language}, '%') AND enabled = 1 ORDER BY priority ASC")
    List<ReviewSkill> findByLanguage(@Param("language") String language);

    @Select("SELECT * FROM review_skill WHERE skill_code = #{skillCode}")
    ReviewSkill findBySkillCode(@Param("skillCode") String skillCode);
}