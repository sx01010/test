package com.mathematics.problem;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mathematics.problem.ProblemDtos.TagDto;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final ProblemService problemService;

    public TagController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    public List<TagDto> tags() {
        return problemService.tagTree();
    }
}
