package com.mathematics.admin;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mathematics.admin.AdminDtos.AdminFeedbackSummary;
import com.mathematics.admin.AdminDtos.ResolveFeedbackRequest;
import com.mathematics.admin.AdminDtos.ResolveFeedbackResponse;
import com.mathematics.identity.CurrentUser;

import jakarta.validation.Valid;

/**
 * A21 纠错工单处理。权限在每个方法里显式要求，和项目其它接口口径一致。
 */
@RestController
@RequestMapping("/api/v1/admin/feedback")
public class AdminFeedbackController {

    private final AdminFeedbackService adminFeedback;

    public AdminFeedbackController(AdminFeedbackService adminFeedback) {
        this.adminFeedback = adminFeedback;
    }

    @GetMapping
    public List<AdminFeedbackSummary> list(CurrentUser me,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) Integer limit) {
        me.requireAdmin();
        return adminFeedback.list(status, limit);
    }

    @PostMapping("/{id}/resolve")
    public ResolveFeedbackResponse resolve(CurrentUser me, @PathVariable long id,
                                           @Valid @RequestBody ResolveFeedbackRequest request) {
        me.requireAdmin();
        return adminFeedback.resolve(me.requireId(), id, request);
    }
}
