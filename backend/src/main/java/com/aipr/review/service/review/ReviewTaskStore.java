package com.aipr.review.service.review;

import com.aipr.review.domain.ReviewComment;
import com.aipr.review.domain.ReviewFile;
import com.aipr.review.domain.ReviewTask;

import java.util.List;
import java.util.Optional;

public interface ReviewTaskStore {

    ReviewTask saveTask(ReviewTask task);

    List<ReviewFile> saveFiles(Long taskId, List<ReviewFile> files);

    List<ReviewComment> saveComments(Long taskId, List<ReviewComment> comments);

    Optional<ReviewTask> findTask(Long taskId);

    List<ReviewFile> findFiles(Long taskId);

    List<ReviewComment> findComments(Long taskId);
}
