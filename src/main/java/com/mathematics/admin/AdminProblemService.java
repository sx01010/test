package com.mathematics.admin;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathematics.admin.AdminDtos.AdminProblemDetail;
import com.mathematics.admin.AdminDtos.AdminProblemSummary;
import com.mathematics.admin.AdminDtos.SourceInput;
import com.mathematics.admin.AdminDtos.UpsertProblemRequest;
import com.mathematics.admin.AdminDtos.UpsertProblemResponse;
import com.mathematics.admin.AdminProblemRepository.DetailRow;
import com.mathematics.admin.AdminProblemRepository.Head;
import com.mathematics.admin.AdminProblemRepository.VersionPayload;
import com.mathematics.judge.GradeOutcome;
import com.mathematics.judge.GradeRequest;
import com.mathematics.judge.GradeResult;
import com.mathematics.judge.GraderException;
import com.mathematics.judge.GraderRegistry;
import com.mathematics.judge.JsonAnswers;
import com.mathematics.judge.ProblemType;
import com.mathematics.problem.ProblemRepository;
import com.mathematics.support.ApiException;
import com.mathematics.support.Json;

/**
 * R16 录入与版本、R17 来源闸门。
 */
@Service
public class AdminProblemService {

    private static final Set<String> ORIGIN_TYPES = Set.of("ORIGINAL", "ADAPTED", "LICENSED", "PUBLIC");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "HIDDEN");
    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final int DEFAULT_MAX_SCORE = 100;
    private static final int MIN_OPTIONS = 2;
    private static final int DEFAULT_LIST_SIZE = 50;
    private static final int MAX_LIST_SIZE = 200;

    private final AdminProblemRepository admin;
    private final ProblemRepository problems;
    private final AuditRepository audit;
    private final GraderRegistry graders;
    private final Json json;

    public AdminProblemService(AdminProblemRepository admin, ProblemRepository problems,
                               AuditRepository audit, GraderRegistry graders, Json json) {
        this.admin = admin;
        this.problems = problems;
        this.audit = audit;
        this.graders = graders;
        this.json = json;
    }

    /**
     * A19：新建、编辑、升版、发布走同一个入口。校验全部在写库之前做完，
     * 被拒的请求不会留下半条版本或半条来源登记。
     */
    @Transactional
    public UpsertProblemResponse upsert(long adminId, UpsertProblemRequest request) {
        Normalized normalized = normalize(request);
        return request.id() == null
                ? create(adminId, request, normalized)
                : revise(adminId, request.id(), request, normalized);
    }

    @Transactional(readOnly = true)
    public List<AdminProblemSummary> list(String status, Integer limit) {
        String wanted = upperOrNull(status);
        if (wanted != null && !STATUSES.contains(wanted)) {
            throw ApiException.invalid("状态只支持 DRAFT / PUBLISHED / HIDDEN");
        }
        int size = limit == null ? DEFAULT_LIST_SIZE : Math.min(Math.max(limit, 1), MAX_LIST_SIZE);
        return admin.list(wanted, size);
    }

    @Transactional(readOnly = true)
    public AdminProblemDetail detail(long problemId) {
        DetailRow row = admin.findDetail(problemId).orElseThrow(() -> ApiException.notFound("题目不存在"));
        return new AdminProblemDetail(row.id(), row.title(), row.problemType(), row.difficulty(), row.grade(),
                row.status(), row.stemMd(), json.read(row.optionsJson()), json.read(row.answerJson()),
                row.explanationMd(), json.read(row.graderConfigJson()), row.maxScore(), row.versionNo(),
                row.versionId(), problems.tagIdsOf(problemId), admin.findSource(problemId).orElse(null));
    }

    private UpsertProblemResponse create(long adminId, UpsertProblemRequest request, Normalized normalized) {
        String status = request.publish() ? PUBLISHED : DRAFT;
        long id = admin.insertProblem(request.title().trim(), normalized.type().name(), normalized.difficulty(),
                request.grade().trim(), status, adminId);
        long versionId = admin.insertVersion(id, 1, request.stemMd(), normalized.optionsJson(),
                normalized.answerJson(), request.explanationMd(), normalized.graderConfigJson(),
                normalized.maxScore(), trimToNull(request.changeNote()), adminId);
        admin.updateCurrentVersion(id, versionId);
        admin.replaceTags(id, normalized.tagIds());
        admin.upsertSource(id, normalized.source());

        if (request.publish()) {
            appendPublishAudit(adminId, id, 1, true, request.changeNote());
        }
        return new UpsertProblemResponse(id, 1, status);
    }

    private UpsertProblemResponse revise(long adminId, long problemId, UpsertProblemRequest request,
                                         Normalized normalized) {
        Head head = admin.findHead(problemId).orElseThrow(() -> ApiException.notFound("题目不存在"));
        VersionPayload current = admin.findCurrentVersion(problemId).orElse(null);
        boolean contentChanged = current == null || contentDiffers(current, request, normalized);

        if (contentChanged && PUBLISHED.equals(head.status()) && trimToNull(request.changeNote()) == null) {
            throw ApiException.invalid("已发布题目的内容变更必须写明 changeNote");
        }

        int versionNo = current == null ? 0 : current.versionNo();
        if (contentChanged) {
            versionNo = admin.nextVersionNo(problemId);
            long versionId = admin.insertVersion(problemId, versionNo, request.stemMd(), normalized.optionsJson(),
                    normalized.answerJson(), request.explanationMd(), normalized.graderConfigJson(),
                    normalized.maxScore(), trimToNull(request.changeNote()), adminId);
            admin.updateCurrentVersion(problemId, versionId);
        }

        admin.updateHead(problemId, request.title().trim(), normalized.type().name(),
                normalized.difficulty(), request.grade().trim());
        admin.replaceTags(problemId, normalized.tagIds());
        admin.upsertSource(problemId, normalized.source());

        // publish=false 不会把已发布的题降回草稿：下线要走独立动作，不能靠「忘记勾选」触发
        boolean promoted = request.publish() && !PUBLISHED.equals(head.status());
        if (promoted) {
            admin.updateStatus(problemId, PUBLISHED);
        }
        String status = promoted ? PUBLISHED : head.status();
        if (PUBLISHED.equals(status) && (promoted || contentChanged)) {
            appendPublishAudit(adminId, problemId, versionNo, promoted, request.changeNote());
        }
        return new UpsertProblemResponse(problemId, versionNo, status);
    }

    private Normalized normalize(UpsertProblemRequest request) {
        ProblemType type = parseType(request.type());
        int difficulty = requireDifficulty(request.difficulty());
        int maxScore = request.maxScore() == null ? DEFAULT_MAX_SCORE : request.maxScore();
        if (maxScore <= 0) {
            throw ApiException.invalid("满分必须大于 0");
        }

        JsonNode options = nullSafe(request.options());
        JsonNode answer = nullSafe(request.answerJson());
        JsonNode graderConfig = nullSafe(request.graderConfig());

        Set<String> optionKeys = validateOptions(type, options);
        validateAnswer(type, answer, graderConfig, maxScore, optionKeys);

        List<Long> tagIds = requireExistingTags(request.tagIds());
        SourceInput source = normalizeSource(request.source());
        if (request.publish()) {
            requireCompleteSource(source);
        }

        return new Normalized(type, difficulty, maxScore,
                optionKeys.isEmpty() ? null : json.write(options),
                json.write(answer), json.write(graderConfig), tagIds, source);
    }

    /**
     * 选择题必须有选项，其余题型不存选项。返回选项键位，供答案校验对照。
     */
    private static Set<String> validateOptions(ProblemType type, JsonNode options) {
        if (type != ProblemType.SINGLE && type != ProblemType.MULTI) {
            return Set.of();
        }
        if (options == null || !options.isArray() || options.size() < MIN_OPTIONS) {
            throw ApiException.invalid("选择题至少要有 " + MIN_OPTIONS + " 个选项");
        }
        Set<String> keys = new LinkedHashSet<>();
        for (JsonNode option : options) {
            String key = requireOptionText(option, "key").toUpperCase(Locale.ROOT);
            requireOptionText(option, "textMd");
            if (!keys.add(key)) {
                throw ApiException.invalid("选项 key 重复：" + key);
            }
        }
        return keys;
    }

    /**
     * 录入时最怕的是答案 JSON 写成判题器读不懂的形状：保存时风平浪静，学生作答时判题直接抛异常。
     * 所以这里不另写一套格式规则，而是把标准答案当成用户答案喂给对应 grader 跑一遍，
     * 判不出全对就拒绝入库。判题逻辑以后怎么改，录入校验都跟着一起变。
     */
    private void validateAnswer(ProblemType type, JsonNode answer, JsonNode graderConfig,
                               int maxScore, Set<String> optionKeys) {
        if (answer == null || !answer.isObject()) {
            throw ApiException.invalid("标准答案必须是 JSON 对象");
        }
        try {
            GradeResult result = graders.grade(type,
                    new GradeRequest(selfCheckAnswer(type, answer, optionKeys), answer, graderConfig, maxScore));
            if (result.result() != GradeOutcome.CORRECT) {
                throw ApiException.invalid("标准答案自检没有判成全对，请检查答案与判题配置");
            }
        } catch (GraderException ex) {
            throw ApiException.invalid("答案或判题配置不合法：" + ex.getMessage());
        }
    }

    /**
     * 多空题的标准答案每空是一组可接受写法，取每空第一个写法拼成用户答案；其余题型两边形状一致，直接复用。
     */
    private static JsonNode selfCheckAnswer(ProblemType type, JsonNode answer, Set<String> optionKeys) {
        switch (type) {
            case SINGLE -> requireKnownKeys(Set.of(JsonAnswers.choice(answer)), optionKeys);
            case MULTI -> requireKnownKeys(JsonAnswers.choices(answer), optionKeys);
            case BLANK -> {
                ArrayNode blanks = JsonNodeFactory.instance.arrayNode();
                JsonAnswers.standardBlanks(answer).forEach(aliases -> blanks.add(aliases.get(0)));
                ObjectNode probe = JsonNodeFactory.instance.objectNode();
                probe.set("blanks", blanks);
                return probe;
            }
            default -> {
                // 判断题与数值题的标准答案就是用户答案的形状
            }
        }
        return answer;
    }

    private static void requireKnownKeys(Set<String> answerKeys, Set<String> optionKeys) {
        for (String key : answerKeys) {
            if (!optionKeys.contains(key)) {
                throw new GraderException("答案里的选项 " + key + " 不在选项列表中");
            }
        }
    }

    private List<Long> requireExistingTags(List<Long> tagIds) {
        List<Long> distinct = tagIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            throw ApiException.invalid("至少挂一个知识点");
        }
        if (admin.countExistingTags(distinct) != distinct.size()) {
            throw ApiException.invalid("知识点不存在");
        }
        return distinct;
    }

    private static SourceInput normalizeSource(SourceInput source) {
        String originType = source.originType().trim().toUpperCase(Locale.ROOT);
        if (!ORIGIN_TYPES.contains(originType)) {
            throw ApiException.invalid("来源类型只支持 ORIGINAL / ADAPTED / LICENSED / PUBLIC");
        }
        return new SourceInput(originType, trimToNull(source.contestName()), source.year(),
                trimToNull(source.round()), trimToNull(source.rewriteNote()),
                trimToNull(source.licenseRef()), trimToNull(source.sourceUrl()));
    }

    /**
     * R17：来源字段不齐不给发布。草稿阶段只校验 originType 本身，先录内容后补授权是真实的工作顺序。
     */
    private static void requireCompleteSource(SourceInput source) {
        String missing = switch (source.originType()) {
            case "ADAPTED" -> source.rewriteNote() == null ? "改编说明 rewriteNote" : null;
            case "LICENSED" -> source.licenseRef() == null ? "授权凭证 licenseRef" : null;
            case "PUBLIC" -> source.sourceUrl() == null ? "公开来源链接 sourceUrl" : null;
            default -> null;
        };
        if (missing != null) {
            throw ApiException.invalid("发布前必须补齐" + missing);
        }
    }

    /**
     * 只有内容字段变化才升版。无脑升版会把所有历史提交标成「题目已更新」，
     * 错题本里的那个提示就没意义了。
     */
    private boolean contentDiffers(VersionPayload current, UpsertProblemRequest request, Normalized normalized) {
        return !Objects.equals(current.stemMd(), request.stemMd())
                || !Objects.equals(current.explanationMd(), request.explanationMd())
                || current.maxScore() != normalized.maxScore()
                || !sameJson(current.optionsJson(), normalized.optionsJson())
                || !sameJson(current.answerJson(), normalized.answerJson())
                || !sameJson(current.graderConfigJson(), normalized.graderConfigJson());
    }

    /** 比 JsonNode 不比字符串：键顺序或空白不同不该算内容变更。 */
    private boolean sameJson(String stored, String incoming) {
        return Objects.equals(json.read(stored), json.read(incoming));
    }

    private void appendPublishAudit(long adminId, long problemId, int versionNo,
                                    boolean firstPublish, String changeNote) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("versionNo", versionNo);
        detail.put("firstPublish", firstPublish);
        String note = trimToNull(changeNote);
        if (note != null) {
            detail.put("changeNote", note);
        }
        audit.append(adminId, "PROBLEM_PUBLISH", "PROBLEM", problemId, json.write(detail));
    }

    private static ProblemType parseType(String type) {
        try {
            return ProblemType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.invalid("未知题型：" + type);
        }
    }

    private static int requireDifficulty(Integer difficulty) {
        if (difficulty < 2 || difficulty > 4) {
            throw ApiException.invalid("难度只支持 2 入门 / 3 进阶 / 4 挑战");
        }
        return difficulty;
    }

    private static String requireOptionText(JsonNode option, String field) {
        JsonNode value = option == null ? null : option.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw ApiException.invalid("每个选项都要有非空的 " + field);
        }
        return value.asText().trim();
    }

    /** Jackson 把 JSON 里显式的 null 映射成 NullNode，序列化回去会变成字符串 "null" 落进 JSON 列。 */
    private static JsonNode nullSafe(JsonNode node) {
        return node == null || node.isNull() ? null : node;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String upperOrNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private record Normalized(
            ProblemType type,
            int difficulty,
            int maxScore,
            String optionsJson,
            String answerJson,
            String graderConfigJson,
            List<Long> tagIds,
            SourceInput source) {
    }
}
