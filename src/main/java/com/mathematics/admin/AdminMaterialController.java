package com.mathematics.admin;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mathematics.identity.CurrentUser;
import com.mathematics.material.MaterialDtos.UploadResult;
import com.mathematics.material.MaterialDtos.UploadTicketRequest;
import com.mathematics.material.MaterialDtos.UploadTicketResponse;
import com.mathematics.material.MaterialDtos.UpsertMaterialRequest;
import com.mathematics.material.MaterialDtos.UpsertMaterialResponse;

import jakarta.validation.Valid;

/**
 * A24 / A25 资料上传与发布，外加一个 OpenAPI 里没写的下架接口（R23 需要）。
 */
@RestController
@RequestMapping("/api/v1/admin/materials")
public class AdminMaterialController {

    private final AdminMaterialService adminMaterials;

    public AdminMaterialController(AdminMaterialService adminMaterials) {
        this.adminMaterials = adminMaterials;
    }

    @PostMapping("/upload-ticket")
    public UploadTicketResponse ticket(CurrentUser me, @Valid @RequestBody UploadTicketRequest request) {
        me.requireAdmin();
        return adminMaterials.createTicket(request);
    }

    /**
     * 收字节。签名之外仍然要求管理员身份：签名是给「这个文件、这段时间」授权的，
     * 不该顺带把「谁都能往库里塞文件」也授权出去。
     */
    @PostMapping("/upload")
    public UploadResult upload(CurrentUser me, @RequestParam("t") String token, @RequestBody byte[] bytes) {
        me.requireAdmin();
        return adminMaterials.upload(token, bytes);
    }

    @PostMapping
    public UpsertMaterialResponse upsert(CurrentUser me, @Valid @RequestBody UpsertMaterialRequest request) {
        me.requireAdmin();
        return adminMaterials.upsert(me.requireId(), request);
    }

    @PostMapping("/{id}/takedown")
    public UpsertMaterialResponse takedown(CurrentUser me, @PathVariable long id,
                                           @RequestParam(required = false) String remark) {
        me.requireAdmin();
        return adminMaterials.takedown(me.requireId(), id, remark);
    }
}
