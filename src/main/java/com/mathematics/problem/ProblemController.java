package com.mathematics.problem;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/{id}/explanation")
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
