package com.mathematics.problem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathematics.identity.CurrentUser;
import com.mathematics.judge.ProblemType;
import com.mathematics.problem.ProblemDtos.ExplanationResponse;
import com.mathematics.problem.ProblemDtos.ProblemDetail;
import com.mathematics.problem.ProblemDtos.ProblemSummary;
import com.mathematics.problem.ProblemDtos.SearchQuery;
import com.mathematics.problem.ProblemDtos.SimilarProblem;
import com.mathematics.problem.ProblemDtos.SourcePublic;
import com.mathematics.problem.ProblemDtos.TagDto;
import com.mathematics.support.ApiException;
import com.mathematics.support.CursorPage;
import com.mathematics.support.ErrorCode;
import com.mathematics.support.Json;

@Service
@Transactional(readOnly = true)
public class ProblemService {

    private final ProblemRepository problems;
    private final TagRepository tags;
    private final Json json;

    public ProblemService(ProblemRepository problems, TagRepository tags, Json json) {
        this.problems = problems;
        this.tags = tags;
        this.json = json;
    }

    public List<TagDto> tagTree() {
        return tags.findAll().stream().map(TagDto::of).toList();
    }

    public CursorPage<ProblemSummary> search(String types, Long tagId, Integer difficulty, String grade,
                                            String origin, String keyword, String cursor, Integer limit) {
        int size = CursorPage.normalizeLimit(limit);
        SearchQuery query = new SearchQuery(parseTypes(types), tagId, difficulty,
                trimToNull(grade), upperOrNull(origin), trimToNull(keyword));

        List<ProblemRows.Summary> rows = problems.search(query, CursorPage.parseCursor(cursor), size + 1);
        Map<Long, List<TagRow>> tagsByProblem = problems.tagsByProblemId(rows.stream().map(ProblemRows.Summary::id).toList());

        List<ProblemSummary> items = new ArrayList<>(rows.size());
        for (ProblemRows.Summary row : rows) {
            items.add(new ProblemSummary(row.id(), row.title(), row.problemType(), row.difficulty(), row.grade(),
                    tagsByProblem.getOrDefault(row.id(), List.of()).stream().map(TagDto::of).toList(),
                    row.originType(), row.currentVersionId()));
        }
        return CursorPage.of(items, size, ProblemSummary::id);
    }

    public ProblemDetail detail(long problemId) {
        ProblemRows.Content content = requireContent(problemId);
        SourcePublic source = SourcePublic.of(problems.findSource(problemId).orElse(null));
        List<TagDto> problemTags = problems.tagsByProblemId(List.of(problemId))
                .getOrDefault(problemId, List.of()).stream().map(TagDto::of).toList();

        return new ProblemDetail(content.problemId(), content.title(), content.problemType(), content.stemMd(),
                json.read(content.optionsJson()), blankCount(content), problemTags, source, content.difficulty(),
                content.grade(), content.maxScore(), content.versionId(), content.versionNo());
    }

    /**
     * 多空题只把「有几个空」告诉前端，答案内容仍然只在解析接口里出现。
     */
    private Integer blankCount(ProblemRows.Content content) {
        if (!ProblemType.BLANK.name().equals(content.problemType())) {
            return null;
        }
        JsonNode answer = json.read(content.answerJson());
        JsonNode blanks = answer == null ? null : answer.get("blanks");
        return blanks != null && blanks.isArray() ? blanks.size() : null;
    }

    /**
     * R06：默认随时可看。只有用户自己打开了练习模式、且这道题还没有提交记录时才拦。
     */
    public ExplanationResponse explanation(long problemId, CurrentUser me) {
        ProblemRows.Content content = requireContent(problemId);
        if (me.loggedIn() && me.practiceMode() && !problems.hasSubmission(me.id(), problemId)) {
            throw new ApiException(ErrorCode.CONTENT_NOT_VISIBLE, "你开启了「做完再看解析」，先提交一次再来看");
        }
        return new ExplanationResponse(content.explanationMd(), json.read(content.answerJson()));
    }

    /**
     * R07：同知识点、难度 ±1 取 5 道；候选不足时放宽难度再补一轮。
     */
    public List<SimilarProblem> similar(long problemId, Integer limit, CurrentUser me) {
        ProblemRows.Content content = requireContent(problemId);
        List<Long> tagIds = problems.tagIdsOf(problemId);
        int size = limit == null ? 5 : Math.min(Math.max(limit, 1), 10);
        Long excludeSolvedBy = me.loggedIn() ? me.id() : null;

        List<ProblemRows.Similar> picked = new ArrayList<>(
                problems.findSimilar(problemId, tagIds, content.difficulty(), excludeSolvedBy, true, size));
        if (picked.size() < size) {
            Set<Long> seen = new LinkedHashSet<>(picked.stream().map(ProblemRows.Similar::id).toList());
            problems.findSimilar(problemId, tagIds, content.difficulty(), excludeSolvedBy, false, size).stream()
                    .filter(row -> !seen.contains(row.id()))
                    .limit((long) size - picked.size())
                    .forEach(picked::add);
        }
        return picked.stream().map(SimilarProblem::of).toList();
    }

    private ProblemRows.Content requireContent(long problemId) {
        return problems.findPublishedContent(problemId)
                .orElseThrow(() -> ApiException.notFound("题目不存在或未发布"));
    }

    private static List<String> parseTypes(String types) {
        if (types == null || types.isBlank()) {
            return List.of();
        }
        return Arrays.stream(types.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .peek(ProblemService::requireKnownType)
                .distinct()
                .toList();
    }

    private static void requireKnownType(String type) {
        try {
            ProblemType.valueOf(type);
        } catch (IllegalArgumentException ex) {
            throw ApiException.invalid("未知题型：" + type);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String upperOrNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
