package com.mathematics.admin;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mathematics.admin.AdminDtos.AdminFeedbackSummary;
import com.mathematics.admin.AdminDtos.ResolveFeedbackRequest;
import com.mathematics.admin.AdminDtos.ResolveFeedbackResponse;
import com.mathematics.admin.AdminDtos.UpsertProblemRequest;
import com.mathematics.admin.AdminDtos.UpsertProblemResponse;
import com.mathematics.practice.RegradeService;
import com.mathematics.quality.FeedbackRepository;
import com.mathematics.quality.FeedbackRepository.Ticket;
import com.mathematics.support.ApiException;
import com.mathematics.support.Json;

/**
 * R18：纠错工单结案与回溯重判的编排。
 *
 * <p>升版交给 {@link AdminProblemService}，重判交给 {@link RegradeService}，这里只负责把顺序排对
 * 并保证整件事在一个事务里：先升版再重判，中途失败全部回滚。半成功的结案最难查——
 * 题目答案改了但历史没重判，而工单已经关了，没人会再回来看它。
 */
@Service
public class AdminFeedbackService {

    private static final String FIXED = "FIXED";
    private static final String REJECTED = "REJECTED";
    private static final Set<String> STATUSES = Set.of("OPEN", FIXED, REJECTED);
    private static final int DEFAULT_LIST_SIZE = 50;
    private static final int MAX_LIST_SIZE = 200;

    private final FeedbackRepository feedback;
    private final AdminProblemService adminProblems;
    private final RegradeService regrades;
    private final AuditRepository audit;
    private final Json json;

    public AdminFeedbackService(FeedbackRepository feedback, AdminProblemService adminProblems,
                                RegradeService regrades, AuditRepository audit, Json json) {
        this.feedback = feedback;
        this.adminProblems = adminProblems;
        this.regrades = regrades;
        this.audit = audit;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<AdminFeedbackSummary> list(String status, Integer limit) {
        String wanted = status == null || status.isBlank() ? "OPEN" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(wanted)) {
            throw ApiException.invalid("状态只支持 OPEN / FIXED / REJECTED");
        }
        int size = limit == null ? DEFAULT_LIST_SIZE : Math.min(Math.max(limit, 1), MAX_LIST_SIZE);
        return feedback.listByStatus(wanted, size).stream().map(AdminFeedbackService::toSummary).toList();
    }

    @Transactional
    public ResolveFeedbackResponse resolve(long adminId, long feedbackId, ResolveFeedbackRequest request) {
        String decision = decisionOf(request.decision());
        Ticket ticket = feedback.findById(feedbackId)
                .orElseThrow(() -> ApiException.notFound("纠错工单不存在"));
        if (!ticket.open()) {
            throw ApiException.invalid("这条纠错已经处理过了，当前状态：" + ticket.status());
        }

        Integer versionNo = REJECTED.equals(decision) ? null : applyFix(adminId, ticket, request);
        int regradedCount = FIXED.equals(decision) && request.regrade()
                ? regrades.regradeToCurrentVersion(ticket.problemId(), "regrade-" + feedbackId + "-")
                : 0;

        // 这里再拿一次影响行数：并发下两个管理员同时结案，只有一个能改到行
        if (feedback.resolve(feedbackId, decision, adminId, trimToNull(request.remark())) == 0) {
            throw ApiException.invalid("这条纠错已经被其他人处理了");
        }
        appendAudit(adminId, feedbackId, decision, versionNo, regradedCount, request.remark());

        return new ResolveFeedbackResponse(feedbackId, decision, versionNo, regradedCount);
    }

    /**
     * FIXED 必须真的有修正动作：要么这次带了新版本，要么题目当前版本已经不是工单记录的那一版
     * （管理员先在录题界面改过了）。都不满足就拒绝，否则「标记已修正」会退化成一句空话。
     */
    private Integer applyFix(long adminId, Ticket ticket, ResolveFeedbackRequest request) {
        if (request.newVersion() == null) {
            if (!ticket.problemAlreadyRevised()) {
                throw ApiException.invalid("结论为 FIXED 时要么提交新版本，要么先在录题界面订正并发布题目");
            }
            return null;
        }
        UpsertProblemResponse revised = adminProblems.upsert(adminId, withProblemId(request.newVersion(), ticket));
        return revised.versionNo();
    }

    /**
     * 强制把 id 钉成工单所属的题目并强制发布：路径里已经指明了是哪条工单，
     * 请求体里再给一个题目 id 只会带来「改到别的题上去」这一种结果。
     */
    private static UpsertProblemRequest withProblemId(UpsertProblemRequest source, Ticket ticket) {
        return new UpsertProblemRequest(ticket.problemId(), source.title(), source.type(), source.difficulty(),
                source.grade(), source.stemMd(), source.options(), source.answerJson(), source.explanationMd(),
                source.graderConfig(), source.maxScore(), source.tagIds(), source.source(), source.changeNote(),
                true);
    }

    private void appendAudit(long adminId, long feedbackId, String decision, Integer versionNo,
                             int regradedCount, String remark) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("decision", decision);
        if (versionNo != null) {
            detail.put("versionNo", versionNo);
        }
        detail.put("regradedCount", regradedCount);
        String note = trimToNull(remark);
        if (note != null) {
            detail.put("remark", note);
        }
        String action = FIXED.equals(decision) ? "FEEDBACK_FIX" : "FEEDBACK_REJECT";
        audit.append(adminId, action, "FEEDBACK", feedbackId, json.write(detail));
    }

    private static String decisionOf(String decision) {
        String wanted = decision.trim().toUpperCase(Locale.ROOT);
        if (!FIXED.equals(wanted) && !REJECTED.equals(wanted)) {
            throw ApiException.invalid("处理结论只支持 FIXED / REJECTED");
        }
        return wanted;
    }

    private static AdminFeedbackSummary toSummary(Ticket ticket) {
        return new AdminFeedbackSummary(ticket.id(), ticket.problemId(), ticket.problemTitle(), ticket.reason(),
                ticket.detail(), ticket.status(), ticket.problemAlreadyRevised(),
                ticket.createdAt() == null ? null : ticket.createdAt().format(DateTimeFormatter.ISO_DATE_TIME));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
