package com.mathematics.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mathematics.config.AppProperties;
import com.mathematics.material.MaterialDtos.UploadResult;
import com.mathematics.material.MaterialDtos.UploadTicketRequest;
import com.mathematics.material.MaterialDtos.UploadTicketResponse;
import com.mathematics.material.MaterialDtos.UpsertMaterialRequest;
import com.mathematics.material.MaterialDtos.UpsertMaterialResponse;
import com.mathematics.material.MaterialRepository;
import com.mathematics.material.MaterialService;
import com.mathematics.material.MaterialStorage;
import com.mathematics.material.SignedLinks;
import com.mathematics.support.ApiException;
import com.mathematics.support.Json;

/**
 * R22 上传与发布、R23 下架。
 */
@Service
public class AdminMaterialService {

    public static final String UPLOAD_PURPOSE = "material-upload";

    private static final String PDF_MIME = "application/pdf";
    /** PDF 的魔数。校验它而不是扩展名——扩展名是客户端说了算的东西。 */
    private static final byte[] PDF_MAGIC = "%PDF".getBytes(StandardCharsets.US_ASCII);
    private static final Set<String> TYPES = Set.of("PAPER", "HANDOUT");
    private static final Set<String> ORIGIN_TYPES = Set.of("OWNED", "LICENSED", "PUBLIC");
    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String HIDDEN = "HIDDEN";
    private static final String CLEAN = "CLEAN";

    private final MaterialRepository materials;
    private final MaterialStorage storage;
    private final SignedLinks signedLinks;
    private final AuditRepository audit;
    private final Json json;
    private final AppProperties.Material config;

    public AdminMaterialService(MaterialRepository materials, MaterialStorage storage, SignedLinks signedLinks,
                                AuditRepository audit, Json json, AppProperties properties) {
        this.materials = materials;
        this.storage = storage;
        this.signedLinks = signedLinks;
        this.audit = audit;
        this.json = json;
        this.config = properties.material();
    }

    /**
     * A24：发凭证，不写库。期望的大小与摘要进签名，上传时逐项比对，
     * 所以不需要为「待上传」这个中间状态建表——{@code material_file.material_id} 是 NOT NULL 外键，
     * 资料行还不存在，本来也插不了一条 PENDING 的文件行。
     */
    public UploadTicketResponse createTicket(UploadTicketRequest request) {
        if (!PDF_MIME.equalsIgnoreCase(request.mimeType().trim())) {
            throw ApiException.invalid("V1 只接受 PDF");
        }
        if (request.sizeBytes() > config.maxSizeBytes()) {
            throw ApiException.invalid("文件不能超过 " + config.maxSizeBytes() / 1024 / 1024 + " MB");
        }

        String objectKey = storage.newObjectKey(LocalDate.now().getYear(), request.fileName());
        Instant expiresAt = Instant.now().plus(config.uploadTtl());
        String token = signedLinks.sign(UPLOAD_PURPOSE, objectKey, expiresAt,
                String.valueOf(request.sizeBytes()), request.sha256().toLowerCase(Locale.ROOT));
        return new UploadTicketResponse(objectKey, "/api/v1/admin/materials/upload?t=" + token,
                MaterialService.format(expiresAt));
    }

    /**
     * 收字节。验签 → 实际大小对上声明 → 实际 sha256 对上声明 → 魔数是 PDF → 落盘。
     *
     * <p>「扫描」在 V1 只有这层结构校验，过了就记 CLEAN。这里的 CLEAN 是
     * 「结构上是个 PDF 且和凭证吻合」，**不是「无毒」**。接 ClamAV 时换掉这一个方法。
     */
    public UploadResult upload(String token, byte[] bytes) {
        var claims = signedLinks.verify(UPLOAD_PURPOSE, token)
                .orElseThrow(() -> ApiException.forbidden("上传凭证无效或已过期"));

        long expectedSize = Long.parseLong(claims.claim(0));
        String expectedSha256 = claims.claim(1);
        if (bytes.length != expectedSize) {
            throw ApiException.invalid("文件大小和申请凭证时声明的不一致");
        }
        if (!sha256(bytes).equals(expectedSha256)) {
            throw ApiException.invalid("文件摘要和申请凭证时声明的不一致");
        }
        if (!looksLikePdf(bytes)) {
            throw ApiException.invalid("文件内容不是 PDF");
        }

        storage.write(claims.objectKey(), bytes);
        return new UploadResult(claims.objectKey(), bytes.length, CLEAN);
    }

    /**
     * A25：认领一个已上传的文件，建资料行。大小与摘要从磁盘上的文件重新算一遍，
     * 不信客户端第二次报的数。
     */
    @Transactional
    public UpsertMaterialResponse upsert(long adminId, UpsertMaterialRequest request) {
        String type = requireOneOf(request.type(), TYPES, "资料类型只支持 PAPER / HANDOUT");
        String originType = requireOneOf(request.originType(), ORIGIN_TYPES,
                "来源类型只支持 OWNED / LICENSED / PUBLIC");
        String licenseRef = trimToNull(request.licenseRef());
        String sourceUrl = trimToNull(request.sourceUrl());
        if (request.publish()) {
            requireCompleteSource(originType, licenseRef, sourceUrl);
        }

        String objectKey = request.objectKey().trim();
        if (!storage.exists(objectKey)) {
            throw ApiException.invalid("文件还没上传完，或者 objectKey 不对");
        }
        if (materials.findByObjectKey(objectKey).isPresent()) {
            throw ApiException.invalid("这个文件已经登记过了");
        }

        String status = request.publish() ? PUBLISHED : DRAFT;
        long id = materials.insertMaterial(request.title().trim(), type, trimToNull(request.grade()),
                request.year(), originType, licenseRef, sourceUrl, status, adminId);
        materials.insertFile(id, objectKey, storage.originalNameOf(objectKey), storage.sizeOf(objectKey),
                sha256(storage.read(objectKey)), CLEAN);

        if (request.publish()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("originType", originType);
            detail.put("objectKey", objectKey);
            audit.append(adminId, "MATERIAL_PUBLISH", "MATERIAL", id, json.write(detail));
        }
        return new UpsertMaterialResponse(id, status);
    }

    /**
     * R23：立刻下架。状态改 HIDDEN，文件打延迟清理标记。
     * 已经发出去的签名链接会在下一次兑现时被状态回查挡掉，所以是立刻失效。
     */
    @Transactional
    public UpsertMaterialResponse takedown(long adminId, long materialId, String remark) {
        if (materials.updateStatus(materialId, HIDDEN) == 0) {
            throw ApiException.notFound("资料不存在");
        }
        materials.markFilesDeleted(materialId);

        Map<String, Object> detail = new LinkedHashMap<>();
        String note = trimToNull(remark);
        if (note != null) {
            detail.put("remark", note);
        }
        audit.append(adminId, "MATERIAL_TAKEDOWN", "MATERIAL", materialId, json.write(detail));
        return new UpsertMaterialResponse(materialId, HIDDEN);
    }

    /** R22：来源字段不齐不给发布。草稿阶段允许先欠着，先上传后补授权是真实的工作顺序。 */
    private static void requireCompleteSource(String originType, String licenseRef, String sourceUrl) {
        String missing = switch (originType) {
            case "LICENSED" -> licenseRef == null ? "授权凭证 licenseRef" : null;
            case "PUBLIC" -> sourceUrl == null ? "公开来源链接 sourceUrl" : null;
            default -> null;
        };
        if (missing != null) {
            throw ApiException.invalid("发布前必须补齐" + missing);
        }
    }

    private static boolean looksLikePdf(byte[] bytes) {
        if (bytes.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM 没有 SHA-256", ex);
        }
    }

    private static String requireOneOf(String value, Set<String> allowed, String message) {
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(upper)) {
            throw ApiException.invalid(message);
        }
        return upper;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
