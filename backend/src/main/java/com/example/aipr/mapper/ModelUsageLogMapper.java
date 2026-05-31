package com.example.aipr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aipr.entity.ModelUsageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ModelUsageLogMapper extends BaseMapper<ModelUsageLog> {

    @Select("SELECT * FROM model_usage_log WHERE created_at BETWEEN #{startDate} AND #{endDate} ORDER BY created_at DESC")
    List<ModelUsageLog> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Select("SELECT * FROM model_usage_log WHERE task_id = #{taskId} ORDER BY created_at DESC")
    List<ModelUsageLog> findByTaskId(@Param("taskId") Long taskId);
}