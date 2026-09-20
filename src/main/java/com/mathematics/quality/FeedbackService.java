package com.mathematics.quality;

import java.util.Locale;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mathematics.problem.ProblemRepository;
import com.mathematics.problem.ProblemRows;
import com.mathematics.quality.QualityDtos.CreateFeedbackRequest;
import com.mathematics.quality.QualityDtos.FeedbackCreated;
import com.mathematics.support.ApiException;
import com.mathematics.support.ErrorCode;

@Service
public class FeedbackService {

    private static final Set<String> REASONS = Set.of("ANSWER_ERROR", "TYPO", "UNCLEAR", "OTHER");

    private final FeedbackRepository feedback;
    private final ProblemRepository problems;

    public FeedbackService(FeedbackRepository feedback, ProblemRepository problems) {
        this.feedback = feedback;
        this.problems = problems;
    }

    /**
     * R15：同一人对同一题未结案时只保留一条。MySQL 上有生成列唯一索引兜底，
     * H2 没有那种写法，所以这里先查后插并捕获唯一冲突。
     */
    @Transactional
    public FeedbackCreated submit(long userId, long problemId, CreateFeedbackRequest request) {
        String reason = normalizeReason(request.reason());
        ProblemRows.Content content = problems.findPublishedContent(problemId)
                .orElseThrow(() -> ApiException.notFound("题目不存在或未发布"));

        var existing = feedback.findOpenId(userId, problemId);
        if (existing.isPresent()) {
            return new FeedbackCreated(existing.get(), "OPEN", true);
        }

        try {
            long id = feedback.insertOpen(userId, problemId, content.versionId(), reason, request.detail());
            return new FeedbackCreated(id, "OPEN", false);
        } catch (DuplicateKeyException ex) {
            return feedback.findOpenId(userId, problemId)
                    .map(id -> new FeedbackCreated(id, "OPEN", true))
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "纠错工单冲突后未能回读"));
        }
    }

    private static String normalizeReason(String reason) {
        String value = reason.trim().toUpperCase(Locale.ROOT);
        if (!REASONS.contains(value)) {
            throw ApiException.invalid("reason 只支持 ANSWER_ERROR / TYPO / UNCLEAR / OTHER");
        }
        return value;
    }
}
