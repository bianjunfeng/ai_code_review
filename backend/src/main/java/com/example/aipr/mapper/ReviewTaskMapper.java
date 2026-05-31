package com.example.aipr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aipr.entity.ReviewTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
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

    ReviewTask findLatestSuccessTaskForCache(@Param("ownerName") String ownerName,
                                             @Param("repoName") String repoName,
                                             @Param("prNumber") Integer prNumber,
                                             @Param("headSha") String headSha,
                                             @Param("modelName") String modelName,
                                             @Param("promptVersion") String promptVersion);

    @Select("SELECT COUNT(*) FROM review_task WHERE status = 'SUCCESS'")
    Long countSuccess();

    @Select("SELECT COUNT(*) FROM review_task WHERE status = 'FAILED'")
    Long countFailed();

    @Select("SELECT COUNT(*) FROM review_task WHERE status IN ('PENDING', 'FETCHING_PR', 'PARSING_DIFF', 'REVIEWING', 'SUMMARIZING', 'SCORING')")
    Long countRunning();

    @Select("SELECT COUNT(*) FROM review_task WHERE created_at >= #{startOfDay}")
    Long countToday(@Param("startOfDay") LocalDateTime startOfDay);

    @Select("SELECT COUNT(*) FROM review_task WHERE risk_level IN ('HIGH', 'CRITICAL')")
    Long countHighRisk();

    @Select("SELECT COUNT(*) FROM review_task WHERE risk_level = 'MEDIUM'")
    Long countMediumRisk();

    @Select("SELECT COUNT(*) FROM review_task WHERE risk_level = 'LOW'")
    Long countLowRisk();

    @Select("SELECT * FROM review_task WHERE status = 'FAILED' ORDER BY created_at DESC LIMIT #{limit}")
    List<ReviewTask> findRecentFailures(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM review_task WHERE cached_from_task_id IS NOT NULL")
    Long countCacheHits();

    @Select("SELECT COUNT(*) FROM review_task WHERE cached_from_task_id IS NULL")
    Long countCacheMisses();
}
