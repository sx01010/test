package com.mathematics.material;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mathematics.admin.AuditRepository;
import com.mathematics.config.AppProperties;
import com.mathematics.material.MaterialDtos.DownloadUrlResponse;
import com.mathematics.material.MaterialDtos.MaterialSummary;
import com.mathematics.support.ApiException;
import com.mathematics.support.CursorPage;
import com.mathematics.support.Json;

/**
 * R21：公开资料列表与短期下载地址。
 */
@Service
public class MaterialService {

    public static final String DOWNLOAD_PURPOSE = "material-download";

    private static final Set<String> TYPES = Set.of("PAPER", "HANDOUT");

    private final MaterialRepository materials;
    private final SignedLinks signedLinks;
    private final AuditRepository audit;
    private final Json json;
    private final Duration downloadTtl;

    public MaterialService(MaterialRepository materials, SignedLinks signedLinks, AuditRepository audit,
                           Json json, AppProperties properties) {
        this.materials = materials;
        this.signedLinks = signedLinks;
        this.audit = audit;
        this.json = json;
        this.downloadTtl = properties.material().downloadTtl();
    }

    @Transactional(readOnly = true)
    public CursorPage<MaterialSummary> list(String type, String grade, Integer year, String cursor, Integer limit) {
        int size = CursorPage.normalizeLimit(limit);
        var rows = materials.listPublished(normalizeType(type), trimToNull(grade), year,
                CursorPage.parseCursor(cursor), size + 1);
        return CursorPage.of(rows, size, MaterialRows.Summary::id)
                .map(row -> new MaterialSummary(row.id(), row.title(), row.materialType(), row.grade(),
                        row.year(), row.originType(), row.originalName(), row.sizeBytes()));
    }

    /**
     * 签一个短期下载地址并记审计。资料没发布或文件已标删除就当不存在——
     * 对未登录能看到的资源，「存在但不给你」会泄露草稿标题这类信息。
     */
    @Transactional
    public DownloadUrlResponse createDownloadUrl(long userId, long materialId) {
        MaterialRows.Downloadable row = materials.findDownloadable(materialId)
                .filter(MaterialRows.Downloadable::downloadable)
                .orElseThrow(() -> ApiException.notFound("资料不存在或未发布"));

        Instant expiresAt = Instant.now().plus(downloadTtl);
        String token = signedLinks.sign(DOWNLOAD_PURPOSE, row.objectKey(), expiresAt);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("materialId", materialId);
        detail.put("fileName", row.originalName());
        audit.append(userId, "MATERIAL_DOWNLOAD", "MATERIAL", materialId, json.write(detail));

        return new DownloadUrlResponse("/api/v1/materials/download?t=" + token, format(expiresAt));
    }

    /**
     * 兑现下载令牌。除了验签，还要回查资料状态：所以下架后已经发出去的链接立刻失效，
     * 而不是等签名自然过期。规格允许最多 5 分钟，这里做得更严——反正要查库才能拿到文件路径。
     */
    @Transactional(readOnly = true)
    public MaterialRows.Downloadable resolveDownload(String token) {
        String objectKey = signedLinks.verify(DOWNLOAD_PURPOSE, token)
                .orElseThrow(() -> ApiException.forbidden("下载地址无效或已过期"))
                .objectKey();
        return materials.findByObjectKey(objectKey)
                .filter(MaterialRows.Downloadable::downloadable)
                .orElseThrow(() -> ApiException.notFound("资料已下架"));
    }

    private static String normalizeType(String type) {
        String wanted = trimToNull(type);
        if (wanted == null) {
            return null;
        }
        String upper = wanted.toUpperCase(Locale.ROOT);
        if (!TYPES.contains(upper)) {
            throw ApiException.invalid("资料类型只支持 PAPER / HANDOUT");
        }
        return upper;
    }

    public static String format(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(DateTimeFormatter.ISO_DATE_TIME);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
