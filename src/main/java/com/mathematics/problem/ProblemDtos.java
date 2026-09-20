package com.mathematics.problem;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public final class ProblemDtos {

    private ProblemDtos() {
    }

    public record TagDto(long id, Long parentId, String name, String slug, Integer sortOrder) {

        public static TagDto of(TagRow row) {
            return new TagDto(row.id(), row.parentId(), row.name(), row.slug(), row.sortOrder());
        }
    }

    public record ProblemSummary(
            long id,
            String title,
            String type,
            int difficulty,
            String grade,
            List<TagDto> tags,
            String originType,
            Long versionId) {
    }

    /**
     * R05：详情只返回题干、选项、知识点与出处，答案和解析由独立接口返回。
     *
     * @param blankCount 多空题的空数。只有 BLANK 会有值，前端靠它决定渲染几个输入框，
     *                   它是答案的长度而不是答案本身，不构成泄题。
     */
    public record ProblemDetail(
            long id,
            String title,
            String type,
            String stemMd,
            JsonNode options,
            Integer blankCount,
            List<TagDto> tags,
            SourcePublic source,
            int difficulty,
            String grade,
            int maxScore,
            long versionId,
            int versionNo) {
    }

    /**
     * 对外只暴露标注性信息，rewrite_note 与 license_ref 属于内部登记，不出接口。
     */
    public record SourcePublic(String originType, String contestName, Integer year, String round) {

        public static SourcePublic of(ProblemRows.Source row) {
            return row == null ? null
                    : new SourcePublic(row.originType(), row.contestName(), row.year(), row.round());
        }
    }

    public record ExplanationResponse(String explanationMd, JsonNode answerJson) {
    }

    public record SimilarProblem(long id, String title, int difficulty, int sharedTagCount) {

        public static SimilarProblem of(ProblemRows.Similar row) {
            return new SimilarProblem(row.id(), row.title(), row.difficulty(), row.sharedTagCount());
        }
    }

    /**
     * 题库检索条件。type 支持逗号分隔多选。
     */
    public record SearchQuery(
            List<String> types,
            Long tagId,
            Integer difficulty,
            String grade,
            String originType,
            String keyword) {
    }
}
