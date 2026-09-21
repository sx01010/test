package com.mathematics.material;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class MaterialDtos {

    private MaterialDtos() {
    }

    /** A22 公开列表。刻意没有 objectKey：那等于把文件地址公开了。 */
    public record MaterialSummary(
            long id,
            String title,
            String type,
            String grade,
            Integer year,
            String originType,
            String fileName,
            long sizeBytes) {
    }

    public record DownloadUrlResponse(String url, String expiresAt) {
    }

    /**
     * A24 上传凭证。声明的大小与摘要会进签名，所以服务端不必为「待上传」建行，
     * 也不怕客户端真正上传时改口径。
     */
    public record UploadTicketRequest(
            @NotBlank(message = "文件名不能为空") @Size(max = 255) String fileName,
            @NotBlank(message = "mimeType 不能为空") String mimeType,
            @Positive(message = "文件大小必须大于 0") long sizeBytes,
            // @Pattern 放过 null，所以要和 @NotBlank 一起用，否则不传 sha256 会一路走到 NPE
            @NotBlank(message = "sha256 不能为空")
            @Pattern(regexp = "[0-9a-fA-F]{64}", message = "sha256 必须是 64 位十六进制") String sha256) {
    }

    public record UploadTicketResponse(String objectKey, String uploadUrl, String expiresAt) {
    }

    public record UploadResult(String objectKey, long sizeBytes, String scanStatus) {
    }

    /** A25 登记或发布。哪些来源字段必填取决于 originType，而且只在发布时校验。 */
    public record UpsertMaterialRequest(
            @NotBlank(message = "标题不能为空") @Size(max = 200) String title,
            @NotBlank(message = "资料类型不能为空") String type,
            @Size(max = 16) String grade,
            Integer year,
            @NotBlank(message = "来源类型不能为空") String originType,
            @Size(max = 256) String licenseRef,
            @Size(max = 512) String sourceUrl,
            @NotBlank(message = "objectKey 不能为空") String objectKey,
            boolean publish) {
    }

    public record UpsertMaterialResponse(long id, String status) {
    }
}
