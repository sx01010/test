package com.mathematics.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.mathematics.admin.AdminDtos.ImportFailure;
import com.mathematics.admin.AdminDtos.ImportProblemsRequest;
import com.mathematics.admin.AdminDtos.ImportProblemsResponse;
import com.mathematics.admin.AdminDtos.UpsertProblemRequest;
import com.mathematics.support.ApiException;
import com.mathematics.support.Json;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * R19 批量导入。
 *
 * <p><b>这个类刻意不加 {@code @Transactional}。</b>逐条导入复用 {@link AdminProblemService#upsert}，
 * 它自己是事务性的。外层一旦也开事务，所有 upsert 会并进同一个事务，第一条失败时整个事务被标记
 * rollback-only，最后整批一起回滚——接口照样返回「成功 8 条、失败 2 条」，库里一条都没有。
 * 响应对、数据空，是最难发现的那种错。
 *
 * <p>不开事务后，每次调 upsert 都穿过一次代理边界，各自独立开事务并各自提交，
 * 失败那条只回滚自己。
 */
@Service
public class AdminImportService {

    private static final Logger log = LoggerFactory.getLogger(AdminImportService.class);

    /** 200 条按单条 20 毫秒算是 4 秒，还在同步返回的合理范围里。 */
    private static final int MAX_ITEMS = 200;

    private final AdminProblemService adminProblems;
    private final AuditRepository audit;
    private final Validator validator;
    private final Json json;

    public AdminImportService(AdminProblemService adminProblems, AuditRepository audit,
                              Validator validator, Json json) {
        this.adminProblems = adminProblems;
        this.audit = audit;
        this.validator = validator;
        this.json = json;
    }

    public ImportProblemsResponse importProblems(long adminId, ImportProblemsRequest request) {
        List<UpsertProblemRequest> items = request.items();
        if (items.size() > MAX_ITEMS) {
            throw ApiException.invalid("一次最多导入 " + MAX_ITEMS + " 条，当前 " + items.size() + " 条");
        }

        List<ImportFailure> failed = new ArrayList<>();
        int succeeded = 0;
        for (int index = 0; index < items.size(); index++) {
            int line = index + 1;
            String reason = tryImport(adminId, items.get(index));
            if (reason == null) {
                succeeded++;
            } else {
                failed.add(new ImportFailure(line, reason));
            }
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("succeeded", succeeded);
        detail.put("failed", failed.size());
        audit.append(adminId, "PROBLEM_IMPORT", "PROBLEM", 0L, json.write(detail));

        return new ImportProblemsResponse(succeeded, List.copyOf(failed));
    }

    /**
     * 导一条，成功返回 null，失败返回这一行的原因。
     *
     * <p>只吞 {@link ApiException}（业务拒绝）和 {@link DataAccessException}（库层约束拒绝）。
     * 别的异常照旧往上抛：那是代码的 bug，不该被记成「第 7 行有问题」然后混在失败清单里没人看。
     */
    private String tryImport(long adminId, UpsertProblemRequest item) {
        if (item == null) {
            return "条目为空";
        }
        if (item.id() != null) {
            // 导入只新建。混进 id 就会静默覆盖线上题目并升版，而导入这个动作的心理预期是「往里加」
            return "导入只支持新建，不要带 id；修改已有题目请用单条录入接口";
        }

        String violations = validate(item);
        if (violations != null) {
            return violations;
        }

        try {
            adminProblems.upsert(adminId, item);
            return null;
        } catch (ApiException ex) {
            return ex.getMessage();
        } catch (DataAccessException ex) {
            log.warn("批量导入第一行数据被库层拒绝：{}", ex.getMostSpecificCause().getMessage());
            return "写入被数据库拒绝：" + ex.getMostSpecificCause().getMessage();
        }
    }

    /** 手工跑字段校验，用的是和单条接口完全相同的那批注解，只是把结果落到行。 */
    private String validate(UpsertProblemRequest item) {
        Set<ConstraintViolation<UpsertProblemRequest>> violations = validator.validate(item);
        if (violations.isEmpty()) {
            return null;
        }
        return violations.stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .collect(Collectors.joining("；"));
    }
}
