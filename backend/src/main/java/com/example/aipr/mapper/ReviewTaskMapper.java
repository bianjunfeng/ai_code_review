package com.example.aipr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aipr.entity.ReviewTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReviewTaskMapper extends BaseMapper<ReviewTask> {

    @Select("SELECT * FROM review_task WHERE pr_url = #{prUrl} ORDER BY created_at DESC LIMIT 1")
    ReviewTask findByPrUrl(@Param("prUrl") String prUrl);

    @Select("SELECT * FROM review_task WHERE status = #{status} ORDER BY created_at DESC")
    List<ReviewTask> findByStatus(@Param("status") String status);

    @Select("SELECT COUNT(*) FROM review_task WHERE owner_name = #{ownerName} AND repo_name = #{repoName} AND pr_number = #{prNumber}")
    int countByRepoAndPr(@Param("ownerName") String ownerName, @Param("repoName") String repoName, @Param("prNumber") Integer prNumber);
}