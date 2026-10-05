package com.mathematics.problem;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mathematics.guard.RateLimit;
import com.mathematics.guard.RateLimit.By;
import com.mathematics.identity.CurrentUser;
import com.mathematics.problem.ProblemDtos.ExplanationResponse;
import com.mathematics.problem.ProblemDtos.ProblemDetail;
import com.mathematics.problem.ProblemDtos.ProblemSummary;
import com.mathematics.problem.ProblemDtos.SimilarProblem;
import com.mathematics.support.CursorPage;

@RestController
@RequestMapping("/api/v1/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    public CursorPage<ProblemSummary> search(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return problemService.search(type, tagId, difficulty, grade, origin, q, cursor, limit);
    }

    @GetMapping("/{id}")
    public ProblemDetail detail(@PathVariable long id) {
        return problemService.detail(id);
    }

    /**
     * R06 按用户与 IP 双重限流：解析接口带标准答案，不限流等于允许脚本把整个题库的答案扒走。
     * IP 额度放宽，同一个教室或家庭共用出口 IP 时不至于互相挤占。
     */
    @GetMapping("/{id}/explanation")
    @RateLimit(name = "explanation-user", limit = 60, window = "PT1M", by = By.USER_OR_IP)
    @RateLimit(name = "explanation-ip", limit = 300, window = "PT1M")
    public ExplanationResponse explanation(@PathVariable long id, CurrentUser me) {
        return problemService.explanation(id, me);
    }

    @GetMapping("/{id}/similar")
    public List<SimilarProblem> similar(@PathVariable long id,
                                       @RequestParam(required = false) Integer limit,
                                       CurrentUser me) {
        return problemService.similar(id, limit, me);
    }
}
