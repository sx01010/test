package com.mathematics.admin;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mathematics.admin.AdminDtos.AdminProblemDetail;
import com.mathematics.admin.AdminDtos.AdminProblemSummary;
import com.mathematics.admin.AdminDtos.ImportProblemsRequest;
import com.mathematics.admin.AdminDtos.ImportProblemsResponse;
import com.mathematics.admin.AdminDtos.UpsertProblemRequest;
import com.mathematics.admin.AdminDtos.UpsertProblemResponse;
import com.mathematics.guard.RequireAdmin;
import com.mathematics.identity.CurrentUser;

import jakarta.validation.Valid;

/**
 * 管理端题库接口。整个类要求管理员，由 {@link com.mathematics.guard.AccessAspect} 统一校验。
 */
@RestController
@RequireAdmin
@RequestMapping("/api/v1/admin/problems")
public class AdminProblemController {

    private final AdminProblemService adminProblems;
    private final AdminImportService adminImports;

    public AdminProblemController(AdminProblemService adminProblems, AdminImportService adminImports) {
        this.adminProblems = adminProblems;
        this.adminImports = adminImports;
    }

    @PostMapping
    public UpsertProblemResponse upsert(CurrentUser me, @Valid @RequestBody UpsertProblemRequest request) {
        return adminProblems.upsert(me.requireId(), request);
    }

    /**
     * A20 批量导入。{@code @Valid} 只校验外层的 items 非空，**不级联到条目**：
     * 级联会让一个坏条目把整批判成 400，那正好是规格不要的「失败行阻塞其他行」。
     */
    @PostMapping("/import")
    public ImportProblemsResponse importProblems(CurrentUser me,
                                                 @Valid @RequestBody ImportProblemsRequest request) {
        return adminImports.importProblems(me.requireId(), request);
    }

    @GetMapping
    public List<AdminProblemSummary> list(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) Integer limit) {
        return adminProblems.list(status, limit);
    }

    /** 唯一会返回答案与内部来源字段的读接口，只对管理员开放。 */
    @GetMapping("/{id}")
    public AdminProblemDetail detail(@PathVariable long id) {
        return adminProblems.detail(id);
    }
}
