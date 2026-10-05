package com.mathematics.admin;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AdminDtos {

    private AdminDtos() {
    }

    /**
     * A19：录入、编辑与升版共用一个请求体。id 为空表示新建，publish=true 才过来源闸门。
     */
    public record UpsertProblemRequest(
            Long id,
            @NotBlank(message = "标题不能为空") @Size(max = 200, message = "标题最长 200 字") String title,
            @NotBlank(message = "题型不能为空") String type,
            @NotNull(message = "难度不能为空") Integer difficulty,
            @NotBlank(message = "适用年级不能为空") @Size(max = 16) String grade,
            @NotBlank(message = "题干不能为空") String stemMd,
            JsonNode options,
            @NotNull(message = "标准答案不能为空") JsonNode answerJson,
            @NotBlank(message = "解析必填，V1 要求自己撰写") String explanationMd,
            JsonNode graderConfig,
            Integer maxScore,
            List<Long> tagIds,
            // 批量导入的题目文件跨环境复用，库里的 id 各环境不同，slug 才稳定；两者可以混用
            List<String> tagSlugs,
            @NotNull(message = "来源登记不能为空") @Valid SourceInput source,
            @Size(max = 256, message = "变更说明最长 256 字") String changeNote,
            boolean publish) {
    }

    /**
     * 来源登记。哪些字段必填取决于 originType，而且只在发布时才校验，草稿允许先欠着。
     */
    public record SourceInput(
            @NotBlank(message = "来源类型不能为空") String originType,
            @Size(max = 128) String contestName,
            Integer year,
            @Size(max = 32) String round,
            @Size(max = 500, message = "改编说明最长 500 字") String rewriteNote,
            @Size(max = 256) String licenseRef,
            @Size(max = 512) String sourceUrl) {
    }

    public record UpsertProblemResponse(long id, int versionNo, String status) {
    }

    /**
     * 管理端列表。和公开列表不同，这里要能看到 DRAFT，但仍然不带答案。
     */
    public record AdminProblemSummary(
            long id,
            String title,
            String type,
            int difficulty,
            String grade,
            String status,
            String originType,
            Integer versionNo,
            String updatedAt) {
    }

    /**
     * 编辑用详情。这是唯一会把答案、解析和内部来源字段一起返回的接口，只对管理员开放。
     */
    public record AdminProblemDetail(
            long id,
            String title,
            String type,
            int difficulty,
            String grade,
            String status,
            String stemMd,
            JsonNode options,
            JsonNode answerJson,
            String explanationMd,
            JsonNode graderConfig,
            int maxScore,
            int versionNo,
            long versionId,
            List<Long> tagIds,
            SourceInput source) {
    }

    /**
     * A21 结案请求。{@code newVersion} 走的是和录题完全相同的请求体，
     * 所以来源闸门、标准答案自检、changeNote 强制这些规则自动生效——
     * 纠错升版没有理由比正常升版宽松。
     */
    public record ResolveFeedbackRequest(
            @NotBlank(message = "处理结论不能为空") String decision,
            @Valid UpsertProblemRequest newVersion,
            boolean regrade,
            @Size(max = 256, message = "处理说明最长 256 字") String remark) {
    }

    /** regradedCount 是 0 也要返回，管理员得能区分「没受影响」和「重判没生效」。 */
    public record ResolveFeedbackResponse(long id, String status, Integer versionNo, int regradedCount) {
    }

    /**
     * A20 批量导入。
     *
     * <p>元素上**刻意不加** {@code @Valid}：级联校验会在进入方法之前就把整个请求判失败，
     * 返回一个 400，那正好是「失败行阻塞其他行」——规格明确不要的行为。
     * 字段校验改成在循环里手工调 Validator，粒度落到行。
     */
    public record ImportProblemsRequest(
            @NotEmpty(message = "至少要有一条题目") List<UpsertProblemRequest> items) {
    }

    public record ImportFailure(int line, String reason) {
    }

    public record ImportProblemsResponse(int succeeded, List<ImportFailure> failed) {
    }

    /** 纠错队列条目。带题目标题，免得管理员为了知道是哪道题再点一次。 */
    public record AdminFeedbackSummary(
            long id,
            long problemId,
            String problemTitle,
            String reason,
            String detail,
            String status,
            boolean problemAlreadyRevised,
            String createdAt) {
    }
}
