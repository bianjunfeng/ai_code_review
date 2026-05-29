package com.aipr.review.service.review;

import com.aipr.review.domain.ReviewComment;
import com.aipr.review.domain.ReviewFile;
import com.aipr.review.domain.ReviewTask;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryReviewTaskStore implements ReviewTaskStore {

    private final AtomicLong taskIdGenerator = new AtomicLong(1);
    private final AtomicLong fileIdGenerator = new AtomicLong(1);
    private final AtomicLong commentIdGenerator = new AtomicLong(1);
    private final Map<Long, ReviewTask> tasks = new ConcurrentHashMap<>();
    private final Map<Long, List<ReviewFile>> filesByTaskId = new ConcurrentHashMap<>();
    private final Map<Long, List<ReviewComment>> commentsByTaskId = new ConcurrentHashMap<>();

    @Override
    public ReviewTask saveTask(ReviewTask task) {
        LocalDateTime now = LocalDateTime.now();
        if (task.getId() == null) {
            task.setId(taskIdGenerator.getAndIncrement());
            task.setCreatedAt(now);
        }
        task.setUpdatedAt(now);
        tasks.put(task.getId(), task);
        return task;
    }

    @Override
    public List<ReviewFile> saveFiles(Long taskId, List<ReviewFile> files) {
        LocalDateTime now = LocalDateTime.now();
        List<ReviewFile> savedFiles = files.stream()
                .map(file -> {
                    if (file.getId() == null) {
                        file.setId(fileIdGenerator.getAndIncrement());
                        file.setCreatedAt(now);
                    }
                    file.setTaskId(taskId);
                    file.setUpdatedAt(now);
                    return file;
                })
                .toList();
        filesByTaskId.put(taskId, new ArrayList<>(savedFiles));
        return savedFiles;
    }

    @Override
    public List<ReviewComment> saveComments(Long taskId, List<ReviewComment> comments) {
        LocalDateTime now = LocalDateTime.now();
        List<ReviewComment> savedComments = comments.stream()
                .map(comment -> {
                    if (comment.getId() == null) {
                        comment.setId(commentIdGenerator.getAndIncrement());
                        comment.setCreatedAt(now);
                    }
                    comment.setTaskId(taskId);
                    return comment;
                })
                .toList();
        commentsByTaskId.put(taskId, new ArrayList<>(savedComments));
        return savedComments;
    }

    @Override
    public Optional<ReviewTask> findTask(Long taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public List<ReviewFile> findFiles(Long taskId) {
        return List.copyOf(filesByTaskId.getOrDefault(taskId, List.of()));
    }

    @Override
    public List<ReviewComment> findComments(Long taskId) {
        return List.copyOf(commentsByTaskId.getOrDefault(taskId, List.of()));
    }
}
