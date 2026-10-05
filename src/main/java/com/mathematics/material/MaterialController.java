package com.mathematics.material;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mathematics.guard.RateLimit;
import com.mathematics.guard.RateLimit.By;
import com.mathematics.guard.RequireLogin;
import com.mathematics.identity.CurrentUser;
import com.mathematics.material.MaterialDtos.DownloadUrlResponse;
import com.mathematics.material.MaterialDtos.MaterialSummary;
import com.mathematics.support.CursorPage;

/**
 * A22 / A23 资料下载。列表匿名可看——这是获客入口，不该藏在登录后面。
 */
@RestController
@RequestMapping("/api/v1/materials")
public class MaterialController {

    private final MaterialService materials;
    private final MaterialStorage storage;

    public MaterialController(MaterialService materials, MaterialStorage storage) {
        this.materials = materials;
        this.storage = storage;
    }

    @GetMapping
    public CursorPage<MaterialSummary> list(@RequestParam(required = false) String type,
                                            @RequestParam(required = false) String grade,
                                            @RequestParam(required = false) Integer year,
                                            @RequestParam(required = false) String cursor,
                                            @RequestParam(required = false) Integer limit) {
        return materials.list(type, grade, year, cursor, limit);
    }

    @PostMapping("/{id}/download-url")
    @RequireLogin
    @RateLimit(name = "download-url", limit = 30, window = "PT10M", by = By.USER_OR_IP)
    public DownloadUrlResponse downloadUrl(CurrentUser me, @PathVariable long id) {
        return materials.createDownloadUrl(me.requireId(), id);
    }

    /**
     * 兑现签名地址。**刻意不要求登录**：浏览器直接跳转下载时带不了 Authorization 头，
     * 这个接口要是也要求登录，签名 URL 就没有存在的意义了。签名本身就是授权。
     */
    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam("t") String token) {
        MaterialRows.Downloadable file = materials.resolveDownload(token);
        byte[] bytes = storage.read(file.objectKey());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(URLEncoder.encode(file.originalName(), StandardCharsets.UTF_8).replace("+", "%20"))
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", disposition.toString())
                .body(bytes);
    }
}
