package com.mathematics.material;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.mathematics.config.AppProperties;

/**
 * 短期签名令牌。规格要的是对象存储签名下载，这台机器上没有对象存储，
 * 所以自己签一份：{@code base64url(载荷).base64url(HMAC-SHA256)}，URL 里只带一个参数。
 *
 * <p>换成 S3 预签名时整个类可以删掉，签发与校验的调用点各一处。
 */
@Component
public class SignedLinks {

    private static final Logger log = LoggerFactory.getLogger(SignedLinks.class);
    private static final String ALGORITHM = "HmacSHA256";
    private static final char FIELD_SEPARATOR = '|';

    private final byte[] secret;

    public SignedLinks(AppProperties properties) {
        String configured = properties.material().signingSecret();
        if (configured == null || configured.isBlank()) {
            // 刻意不给硬编码兜底：写在仓库里的密钥等于没有签名。随机密钥的代价是重启后旧链接失效，
            // 而链接本来只活几分钟，这个代价不存在。
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secret = random;
            log.warn("mathematics.material.signing-secret 未配置，已随机生成。"
                    + "重启后此前签发的下载与上传链接全部失效；多实例部署必须显式配置同一个值。");
        } else {
            this.secret = configured.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * 签一个令牌。
     *
     * @param purpose 用途。必须进签名，否则下载令牌能拿去当上传令牌用，
     *                一个只能读资料的人就能往存储里写文件。
     * @param claims  附加声明，会原样进签名，校验时按顺序取回
     */
    public String sign(String purpose, String objectKey, Instant expiresAt, String... claims) {
        String payload = payload(purpose, objectKey, expiresAt, claims);
        return encode(payload.getBytes(StandardCharsets.UTF_8)) + "." + encode(hmac(payload));
    }

    /**
     * 校验并解出载荷。验签在验过期之前：反过来的话，一个伪造的令牌会先得到
     * 「已过期」这种带信息量的回答。
     */
    public Optional<Claims> verify(String purpose, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int dot = token.lastIndexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        String payload;
        byte[] signature;
        try {
            payload = new String(decode(token.substring(0, dot)), StandardCharsets.UTF_8);
            signature = decode(token.substring(dot + 1));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        // 定长时间比较，别用 Arrays.equals 给出提前返回的时序差异
        if (!MessageDigest.isEqual(hmac(payload), signature)) {
            return Optional.empty();
        }

        List<String> fields = List.of(payload.split("\\" + FIELD_SEPARATOR, -1));
        if (fields.size() < 3 || !purpose.equals(fields.get(0))) {
            return Optional.empty();
        }
        long expiresAtEpoch;
        try {
            expiresAtEpoch = Long.parseLong(fields.get(2));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        if (Instant.now().getEpochSecond() > expiresAtEpoch) {
            return Optional.empty();
        }
        return Optional.of(new Claims(fields.get(1), fields.subList(3, fields.size())));
    }

    private String payload(String purpose, String objectKey, Instant expiresAt, String... claims) {
        StringBuilder payload = new StringBuilder()
                .append(purpose).append(FIELD_SEPARATOR)
                .append(objectKey).append(FIELD_SEPARATOR)
                .append(expiresAt.getEpochSecond());
        for (String claim : claims) {
            payload.append(FIELD_SEPARATOR).append(claim);
        }
        return payload.toString();
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC 签名失败", ex);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    /** 校验通过后的载荷。{@code claims} 按签发时的顺序。 */
    public record Claims(String objectKey, List<String> claims) {

        public String claim(int index) {
            return index < claims.size() ? claims.get(index) : null;
        }
    }
}
