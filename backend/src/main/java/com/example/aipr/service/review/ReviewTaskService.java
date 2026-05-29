package com.example.aipr.service.review;

import com.example.aipr.enums.ReviewTaskStatus;
import com.example.aipr.service.github.PrUrlParser;
import com.example.aipr.vo.ReviewTaskCreatedVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class ReviewTaskService {

    private final PrUrlParser prUrlParser;
    private final AtomicLong taskIdGenerator = new AtomicLong(1);

    public ReviewTaskCreatedVO createTask(String prUrl) {
        prUrlParser.parse(prUrl);

        return ReviewTaskCreatedVO.builder()
                .taskId(taskIdGenerator.getAndIncrement())
                .status(ReviewTaskStatus.PENDING.name())
                .build();
    }
}
