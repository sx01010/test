package com.mathematics.practice;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mathematics.judge.GradeOutcome;
import com.mathematics.judge.GradeRequest;
import com.mathematics.judge.GradeResult;
import com.mathematics.judge.GraderException;
import com.mathematics.judge.GraderRegistry;
import com.mathematics.judge.ProblemType;
import com.mathematics.problem.ProblemRepository;
import com.mathematics.problem.ProblemRows;
import com.mathematics.support.ApiException;
import com.mathematics.support.Json;

/**
 * R18 的回溯重判。
 *
 * <p>放在 practice 而不是 admin：重判要同时改 submission、wrong_item、user_tag_progress，
 * 这三张表是 practice 的。admin 那边只负责编排结案流程，不直接伸手进别人的表。
 */
@Service
public class RegradeService {

    private final SubmissionRepository submissions;
    private final WrongItemRepository wrongItems;
    private final TagProgressRepository tagProgress;
    private final ProblemRepository problems;
    private final GraderRegistry graders;
    private final Json json;

    public RegradeService(SubmissionRepository submissions, WrongItemRepository wrongItems,
                          TagProgressRepository tagProgress, ProblemRepository problems,
                          GraderRegistry graders, Json json) {
        this.submissions = submissions;
        this.wrongItems = wrongItems;
        this.tagProgress = tagProgress;
        this.problems = problems;
        this.graders = graders;
        this.json = json;
    }

    /**
     * 按题目当前版本重判历史提交，返回实际改判的条数。
     *
     * @param keyPrefix 幂等键前缀，调用方用它保证同一次结案重复执行不会写出两份重判结果
     */
    @Transactional
    public int regradeToCurrentVersion(long problemId, String keyPrefix) {
        ProblemRows.Content content = problems.findPublishedContent(problemId)
                .orElseThrow(() -> ApiException.notFound("题目不存在或未发布，无法重判"));
        ProblemType type = ProblemType.valueOf(content.problemType());
        LocalDateTime now = LocalDateTime.now();

        int changed = 0;
        for (PracticeRows.Regradable original : submissions.listRegradable(problemId, content.versionId())) {
            GradeResult regraded = regrade(type, content, original);
            if (regraded == null || regraded.result().name().equals(original.result())) {
                // 判不动或结果没变就跳过：答案改对了，本来就答对的学生不该多出一条记录
                continue;
            }
            apply(content, original, regraded, keyPrefix, now);
            changed++;
        }
        return changed;
    }

    /**
     * 用学生当时的答案对照新版本重新判一次。
     *
     * <p>答案判不动就返回 null 而不是抛异常：历史答案的形状可能和订正后的题型不再兼容
     * （比如把单选改成了多空），这时候跳过这一条，不该让整次重判连带失败。
     */
    private GradeResult regrade(ProblemType type, ProblemRows.Content content, PracticeRows.Regradable original) {
        try {
            return graders.grade(type, new GradeRequest(json.read(original.answerJson()),
                    json.read(content.answerJson()), json.read(content.graderConfigJson()), content.maxScore()));
        } catch (GraderException ex) {
            return null;
        }
    }

    private void apply(ProblemRows.Content content, PracticeRows.Regradable original,
                       GradeResult regraded, String keyPrefix, LocalDateTime now) {
        String detailsJson = regraded.details() == null || regraded.details().isEmpty()
                ? null : regraded.details().toString();
        long newSubmissionId = submissions.insertRegraded(original.userId(), content.problemId(),
                content.versionId(), original.answerJson(), regraded.result().name(), regraded.score(),
                regraded.maxScore(), detailsJson, keyPrefix + original.id(), original.id());

        // 统计只认对错类别的迁移，不认分数变化。PARTIAL 改判成 WRONG 也算结果变了，得留下新行，
        // 但两者在错题本和正确率里都算「没做对」，这时候动统计会把错误计数加两次、
        // 正确数减掉一个本来就没算进去的。
        boolean wasCorrect = GradeOutcome.CORRECT.name().equals(original.result());
        boolean nowCorrect = regraded.result() == GradeOutcome.CORRECT;
        if (wasCorrect == nowCorrect) {
            return;
        }

        if (nowCorrect) {
            wrongItems.undoWrong(original.userId(), content.problemId(), newSubmissionId, content.versionId());
        } else {
            wrongItems.recordWrong(original.userId(), content.problemId(), newSubmissionId,
                    content.versionId(), now);
        }
        for (Long tagId : problems.tagIdsOf(content.problemId())) {
            tagProgress.adjustCorrect(original.userId(), tagId, nowCorrect ? 1 : -1);
        }
    }
}
