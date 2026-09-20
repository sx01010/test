package com.mathematics.practice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathematics.config.AppProperties;
import com.mathematics.judge.GradeOutcome;
import com.mathematics.judge.GradeRequest;
import com.mathematics.judge.GradeResult;
import com.mathematics.judge.GraderException;
import com.mathematics.judge.GraderRegistry;
import com.mathematics.judge.ProblemType;
import com.mathematics.practice.PracticeDtos.CreateSubmissionRequest;
import com.mathematics.practice.PracticeDtos.MarkMasteredResponse;
import com.mathematics.practice.PracticeDtos.SubmissionDetail;
import com.mathematics.practice.PracticeDtos.SubmissionResult;
import com.mathematics.practice.PracticeDtos.SubmissionSummary;
import com.mathematics.practice.PracticeDtos.TagProgress;
import com.mathematics.practice.PracticeDtos.WrongItem;
import com.mathematics.problem.ProblemRepository;
import com.mathematics.problem.ProblemRows;
import com.mathematics.support.ApiException;
import com.mathematics.support.CursorPage;
import com.mathematics.support.ErrorCode;
import com.mathematics.support.Json;

@Service
public class PracticeService {

    private final SubmissionRepository submissions;
    private final WrongItemRepository wrongItems;
    private final TagProgressRepository tagProgress;
    private final ProblemRepository problems;
    private final GraderRegistry graders;
    private final Json json;
    private final int masteryThreshold;

    public PracticeService(SubmissionRepository submissions, WrongItemRepository wrongItems,
                           TagProgressRepository tagProgress, ProblemRepository problems,
                           GraderRegistry graders, Json json, AppProperties properties) {
        this.submissions = submissions;
        this.wrongItems = wrongItems;
        this.tagProgress = tagProgress;
        this.problems = problems;
        this.graders = graders;
        this.json = json;
        this.masteryThreshold = properties.practice().masteryThreshold();
    }

    /**
     * R08 + R09 + R12 + R14 都落在这一个事务里：判分、幂等、错题本、知识点进度。
     */
    @Transactional
    public SubmissionResult submit(long userId, String idempotencyKey, CreateSubmissionRequest request) {
        String key = requireKey(idempotencyKey);

        // 重试先走这条快路径：同一个 key 直接回原结果，不重复写错题本和进度
        var replay = submissions.findByIdempotencyKey(userId, key);
        if (replay.isPresent()) {
            return toResult(replay.get());
        }

        ProblemRows.Content content = problems.findPublishedContent(request.problemId())
                .orElseThrow(() -> ApiException.notFound("题目不存在或未发布"));
        if (content.versionId() != request.problemVersionId()) {
            throw new ApiException(ErrorCode.PROBLEM_VERSION_STALE, "题目已更新，请刷新后重新作答");
        }

        GradeResult graded = grade(content, request.answer());
        String detailsJson = graded.details() == null || graded.details().isEmpty()
                ? null : graded.details().toString();

        long submissionId;
        try {
            submissionId = submissions.insert(userId, content.problemId(), content.versionId(),
                    request.answer().toString(), graded.result().name(), graded.score(), graded.maxScore(),
                    detailsJson, request.durationMs(), key);
        } catch (DuplicateKeyException ex) {
            // 并发重试撞上唯一索引，回读已落库的那一条
            return submissions.findByIdempotencyKey(userId, key)
                    .map(this::toResult)
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "幂等冲突后未能回读提交"));
        }

        applyPracticeEffects(userId, content, submissionId, graded);

        return new SubmissionResult(submissionId, graded.result().name(), graded.score(), graded.maxScore(),
                graded.details() == null || graded.details().isEmpty() ? null : graded.details());
    }

    @Transactional(readOnly = true)
    public SubmissionDetail detail(long userId, long submissionId) {
        PracticeRows.Submission row = submissions.findById(submissionId)
                .orElseThrow(() -> ApiException.notFound("提交不存在"));
        if (row.userId() != userId) {
            throw ApiException.forbidden("只能查看自己的提交");
        }
        return new SubmissionDetail(row.id(), row.problemId(), row.problemVersionId(), row.versionNo(),
                row.versionChanged(), row.result(), row.score(), row.maxScore(),
                json.read(row.detailsJson()), json.read(row.answerJson()), row.durationMs(), row.createdAt());
    }

    @Transactional(readOnly = true)
    public CursorPage<SubmissionSummary> listMine(long userId, Long problemId, String cursor, Integer limit) {
        int size = CursorPage.normalizeLimit(limit);
        List<PracticeRows.Submission> rows =
                submissions.listByUser(userId, problemId, CursorPage.parseCursor(cursor), size + 1);
        return CursorPage.of(rows, size, PracticeRows.Submission::id)
                .map(row -> new SubmissionSummary(row.id(), row.problemId(), row.title(), row.versionNo(),
                        row.versionChanged(), row.result(), row.score(), row.maxScore(),
                        row.durationMs(), row.createdAt()));
    }

    @Transactional(readOnly = true)
    public CursorPage<WrongItem> listWrongItems(long userId, Integer mastered, Long tagId, String since,
                                                String cursor, Integer limit) {
        int size = CursorPage.normalizeLimit(limit);
        List<PracticeRows.WrongItem> rows = wrongItems.list(userId, mastered, tagId, parseSince(since),
                CursorPage.parseCursor(cursor), size + 1);
        return CursorPage.of(rows, size, PracticeRows.WrongItem::id)
                .map(row -> new WrongItem(row.problemId(), row.title(), row.mastered(), row.wrongCount(),
                        row.consecutiveCorrect(), row.lastWrongAt(), row.versionChanged(), row.lastVersionId()));
    }

    @Transactional
    public MarkMasteredResponse markMastered(long userId, long problemId, boolean mastered) {
        if (wrongItems.setMastered(userId, problemId, mastered) == 0) {
            throw ApiException.notFound("这道题不在你的错题本里");
        }
        return new MarkMasteredResponse(problemId, mastered);
    }

    @Transactional(readOnly = true)
    public List<TagProgress> progress(long userId) {
        return tagProgress.listByUser(userId);
    }

    private GradeResult grade(ProblemRows.Content content, JsonNode answer) {
        ProblemType type = ProblemType.valueOf(content.problemType());
        GradeRequest gradeRequest = new GradeRequest(answer, json.read(content.answerJson()),
                json.read(content.graderConfigJson()), content.maxScore());
        try {
            return graders.grade(type, gradeRequest);
        } catch (GraderException ex) {
            throw ApiException.invalid("答案格式不正确：" + ex.getMessage());
        }
    }

    private void applyPracticeEffects(long userId, ProblemRows.Content content, long submissionId, GradeResult graded) {
        LocalDateTime now = LocalDateTime.now();
        if (graded.result() == GradeOutcome.CORRECT) {
            wrongItems.recordCorrect(userId, content.problemId(), submissionId, masteryThreshold);
        } else {
            wrongItems.recordWrong(userId, content.problemId(), submissionId, content.versionId(), now);
        }
        boolean correct = graded.result() == GradeOutcome.CORRECT;
        for (Long tagId : problems.tagIdsOf(content.problemId())) {
            tagProgress.record(userId, tagId, correct, now);
        }
    }

    private SubmissionResult toResult(PracticeRows.Submission row) {
        return new SubmissionResult(row.id(), row.result(), row.score(), row.maxScore(),
                json.read(row.detailsJson()));
    }

    private static String requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.invalid("缺少 Idempotency-Key 请求头");
        }
        String key = idempotencyKey.trim();
        if (key.length() > 64) {
            throw ApiException.invalid("Idempotency-Key 最长 64 字符");
        }
        return key;
    }

    /**
     * R13 的时间筛选：today / 7d / all。
     */
    private static LocalDateTime parseSince(String since) {
        if (since == null || since.isBlank() || "all".equalsIgnoreCase(since)) {
            return null;
        }
        if ("today".equalsIgnoreCase(since)) {
            return LocalDate.now().atStartOfDay();
        }
        if ("7d".equalsIgnoreCase(since)) {
            return LocalDateTime.now().minusDays(7);
        }
        throw ApiException.invalid("since 只支持 today / 7d / all");
    }
}
