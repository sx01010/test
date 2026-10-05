package com.mathematics.asset;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mathematics.asset.AssetRepository.Asset;
import com.mathematics.material.MaterialStorage;
import com.mathematics.support.ApiException;

/**
 * 题目配图。几何题、计数题离不开图，这是 V1 唯一的图片上传入口，只对管理员开放。
 *
 * <p>类型以文件头魔数为准，不信请求头：Content-Type 是客户端说了算的东西。
 * SVG 是文本，可以藏脚本，所以拒绝任何带脚本、事件属性、外部引用的 SVG，
 * 而不是尝试「清洗」——清洗器漏一个写法就是存储型 XSS。
 */
@Service
public class AssetService {

    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{32}");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", ".png", "image/jpeg", ".jpg", "image/webp", ".webp", "image/svg+xml", ".svg");

    private static final Pattern SVG_FORBIDDEN = Pattern.compile(
            "<\\s*script|<\\s*foreignobject|<\\s*iframe|<\\s*embed|<\\s*object|<!entity"
                    + "|javascript:|data:text/html|\\son[a-z]+\\s*="
                    + "|(?:xlink:)?href\\s*=\\s*[\"']\\s*(?!#)",
            Pattern.CASE_INSENSITIVE);

    private final AssetRepository assets;
    private final MaterialStorage storage;

    public AssetService(AssetRepository assets, MaterialStorage storage) {
        this.assets = assets;
        this.storage = storage;
    }

    public record Uploaded(String key, String url, String markdown) {
        static Uploaded of(String key) {
            return new Uploaded(key, "/api/v1/assets/" + key, "![](asset:" + key + ")");
        }
    }

    public record Served(String mimeType, byte[] bytes) {
    }

    @Transactional
    public Uploaded upload(long adminId, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw ApiException.invalid("图片内容为空");
        }
        if (bytes.length > MAX_BYTES) {
            throw ApiException.invalid("图片不能超过 2 MB");
        }
        String mime = detect(bytes);
        String sha = sha256(bytes);
        var existing = assets.findBySha256(sha, mime);
        if (existing.isPresent()) {
            return Uploaded.of(existing.get().objectKey());
        }
        String key = randomKey();
        storage.write(path(key, mime), bytes);
        assets.insert(key, mime, bytes.length, sha, adminId);
        return Uploaded.of(key);
    }

    @Transactional(readOnly = true)
    public Served serve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw ApiException.notFound("图片不存在");
        }
        Asset asset = assets.findByKey(key).orElseThrow(() -> ApiException.notFound("图片不存在"));
        return new Served(asset.mimeType(), storage.read(path(key, asset.mimeType())));
    }

    static String detect(byte[] b) {
        if (startsWith(b, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(b, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (b.length >= 12 && startsWith(b, 'R', 'I', 'F', 'F')
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        String text = new String(b, StandardCharsets.UTF_8);
        String head = text.stripLeading().toLowerCase(Locale.ROOT);
        if (head.startsWith("<svg") || (head.startsWith("<?xml") && head.contains("<svg"))) {
            if (SVG_FORBIDDEN.matcher(text).find()) {
                throw ApiException.invalid("SVG 里不能有脚本、事件属性或外部引用");
            }
            return "image/svg+xml";
        }
        throw ApiException.invalid("只支持 PNG、JPEG、WebP 和 SVG 图片");
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String path(String key, String mime) {
        return "assets/" + key + EXTENSIONS.get(mime);
    }

    private static String randomKey() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
