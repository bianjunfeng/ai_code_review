package com.example.aipr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aipr.entity.ReviewTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReviewTaskMapper extends BaseMapper<ReviewTask> {

    ReviewTask findByPrUrl(@Param("prUrl") String prUrl);

    List<ReviewTask> findByStatus(@Param("status") String status);

    int countByRepoAndPr(@Param("ownerName") String ownerName, @Param("repoName") String repoName, @Param("prNumber") Integer prNumber);

    void updateStatus(@Param("taskId") Long taskId, @Param("status") String status);

    void updateRiskScore(@Param("taskId") Long taskId,
                         @Param("riskScore") Integer riskScore,
                         @Param("riskLevel") String riskLevel,
                         @Param("summary") String summary,
                         @Param("finalReview") String finalReview);
}
