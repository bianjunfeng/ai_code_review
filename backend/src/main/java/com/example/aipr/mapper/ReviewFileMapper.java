package com.example.aipr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aipr.entity.ReviewFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReviewFileMapper extends BaseMapper<ReviewFile> {

    @Select("SELECT * FROM review_file WHERE task_id = #{taskId} ORDER BY created_at ASC")
    List<ReviewFile> findByTaskId(@Param("taskId") Long taskId);

    @Select("SELECT * FROM review_file WHERE task_id = #{taskId} AND skipped = 0 ORDER BY created_at ASC")
    List<ReviewFile> findActiveByTaskId(@Param("taskId") Long taskId);

    @Select("SELECT COUNT(*) FROM review_file WHERE task_id = #{taskId}")
    int countByTaskId(@Param("taskId") Long taskId);
}