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
import com.mathematics.admin.AdminDtos.UpsertProblemRequest;
import com.mathematics.admin.AdminDtos.UpsertProblemResponse;
import com.mathematics.identity.CurrentUser;

import jakarta.validation.Valid;

/**
 * 管理端题库接口。没有 Spring Security 过滤器链，权限在每个方法里显式要求，
 * 和项目其它接口的口径保持一致。
 */
@RestController
@RequestMapping("/api/v1/admin/problems")
public class AdminProblemController {

    private final AdminProblemService adminProblems;

    public AdminProblemController(AdminProblemService adminProblems) {
        this.adminProblems = adminProblems;
    }

    @PostMapping
    public UpsertProblemResponse upsert(CurrentUser me, @Valid @RequestBody UpsertProblemRequest request) {
        me.requireAdmin();
        return adminProblems.upsert(me.requireId(), request);
    }

    @GetMapping
    public List<AdminProblemSummary> list(CurrentUser me,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) Integer limit) {
        me.requireAdmin();
        return adminProblems.list(status, limit);
    }

    /** 唯一会返回答案与内部来源字段的读接口，只对管理员开放。 */
    @GetMapping("/{id}")
    public AdminProblemDetail detail(CurrentUser me, @PathVariable long id) {
        me.requireAdmin();
        return adminProblems.detail(id);
    }
}
