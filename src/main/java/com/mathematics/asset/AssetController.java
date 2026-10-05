package com.mathematics.asset;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mathematics.asset.AssetService.Served;
import com.mathematics.asset.AssetService.Uploaded;
import com.mathematics.guard.RequireAdmin;
import com.mathematics.identity.CurrentUser;

@RestController
public class AssetController {

    /**
     * 直接打开图片地址时生效：SVG 被当成独立文档渲染，sandbox 禁掉脚本与同源权限。
     * 作为 img 嵌进题干时浏览器本来就不执行 SVG 里的脚本，这一层是给上传校验兜底。
     */
    private static final String ASSET_CSP = "default-src 'none'; style-src 'unsafe-inline'; sandbox";

    private final AssetService assets;

    public AssetController(AssetService assets) {
        this.assets = assets;
    }

    /** 请求体是图片原始字节。类型由服务端按魔数判定，请求头的 Content-Type 只用来让 Spring 收下字节。 */
    @PostMapping("/api/v1/admin/assets")
    @RequireAdmin
    public Uploaded upload(CurrentUser me, @RequestBody byte[] bytes) {
        return assets.upload(me.requireId(), bytes);
    }

    /** 对象键不可预测且内容不可变，所以可以长缓存。题目是匿名可看的，配图也一样。 */
    @GetMapping("/api/v1/assets/{key}")
    public ResponseEntity<byte[]> serve(@PathVariable String key) {
        Served served = assets.serve(key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(served.mimeType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", ASSET_CSP)
                .body(served.bytes());
    }
}
