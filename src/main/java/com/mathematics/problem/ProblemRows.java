package com.mathematics.problem;

/**
 * 数据库行对象。故意和对外 DTO 分开：answer_json 只出现在 Content 里，
 * 列表与详情的返回类型里根本没有这个字段，防止答案顺着列表接口漏出去。
 */
public final class ProblemRows {

    private ProblemRows() {
    }

    public record Summary(
            long id,
            String title,
            String problemType,
            int difficulty,
            String grade,
            Long currentVersionId,
            String originType) {
    }

    public record Content(
            long problemId,
            String title,
            String problemType,
            int difficulty,
            String grade,
            long versionId,
            int versionNo,
            String stemMd,
            String optionsJson,
            String answerJson,
            String explanationMd,
            String graderConfigJson,
            int maxScore) {
    }

    public record Source(String originType, String contestName, Integer year, String round) {
    }

    public record Similar(long id, String title, int difficulty, int sharedTagCount) {
    }

    public record ProblemTag(long problemId, TagRow tag) {
    }
}
