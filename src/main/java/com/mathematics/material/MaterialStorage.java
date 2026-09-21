package com.mathematics.material;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mathematics.config.AppProperties;
import com.mathematics.support.ApiException;

/**
 * 资料文件落在本地磁盘。规格写的是对象存储，但这台机器上没有 S3 或 MinIO，
 * pom 里也没有存储 SDK，所以 V1 先落到本地目录。
 *
 * <p>换成 S3 时只有这个类要重写，签名、闸门、审计都不用动——它们本来就不关心字节存在哪。
 */
@Component
public class MaterialStorage {

    private static final int MAX_NAME_LENGTH = 120;

    private final Path root;

    public MaterialStorage(AppProperties properties) {
        this.root = Path.of(properties.material().storageDir()).toAbsolutePath().normalize();
    }

    /**
     * 生成对象键。uuid 段保证地址不可预测，文件名留在路径里是为了
     * {@code material_file.original_name} 不用另存一处。
     */
    public String newObjectKey(int year, String fileName) {
        return "materials/" + year + "/" + UUID.randomUUID() + "/" + safeName(fileName);
    }

    public String originalNameOf(String objectKey) {
        return objectKey.substring(objectKey.lastIndexOf('/') + 1);
    }

    public void write(String objectKey, byte[] bytes) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException ex) {
            throw new UncheckedIOException("写入资料文件失败：" + objectKey, ex);
        }
    }

    public boolean exists(String objectKey) {
        return Files.isRegularFile(resolve(objectKey));
    }

    public byte[] read(String objectKey) {
        try {
            return Files.readAllBytes(resolve(objectKey));
        } catch (IOException ex) {
            throw new UncheckedIOException("读取资料文件失败：" + objectKey, ex);
        }
    }

    public long sizeOf(String objectKey) {
        try {
            return Files.size(resolve(objectKey));
        } catch (IOException ex) {
            throw new UncheckedIOException("读取资料文件大小失败：" + objectKey, ex);
        }
    }

    /**
     * 解析成绝对路径并确认没跑到根目录外面去。对象键来自签过名的令牌，
     * 但这一层不该依赖「上游一定干净」——路径穿越的代价是整个文件系统。
     */
    private Path resolve(String objectKey) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw ApiException.invalid("对象键不合法");
        }
        return target;
    }

    /**
     * 清洗文件名。留下中英文、数字与少数标点，其余一律换成下划线：
     * 分隔符和 {@code ..} 一旦留在名字里，构造过的文件名就能写到目录外面去。
     */
    private static String safeName(String fileName) {
        String base = fileName == null ? "" : fileName.trim();
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        base = slash < 0 ? base : base.substring(slash + 1);
        base = base.replaceAll("[^\\p{IsHan}\\p{Alnum}._\\-]", "_").replace("..", "_");
        if (base.length() > MAX_NAME_LENGTH) {
            base = base.substring(base.length() - MAX_NAME_LENGTH);
        }
        if (base.isBlank() || base.equals("_")) {
            base = "material.pdf";
        }
        return base.toLowerCase(Locale.ROOT).endsWith(".pdf") ? base : base + ".pdf";
    }
}
