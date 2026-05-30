package com.example.aipr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aipr.entity.ReviewFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReviewFileMapper extends BaseMapper<ReviewFile> {

    List<ReviewFile> findByTaskId(@Param("taskId") Long taskId);

    List<ReviewFile> findActiveByTaskId(@Param("taskId") Long taskId);

    int countByTaskId(@Param("taskId") Long taskId);

    void insertBatch(@Param("list") List<ReviewFile> files);
}
