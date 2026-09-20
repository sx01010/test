package com.mathematics.practice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mathematics.identity.CurrentUser;
import com.mathematics.practice.PracticeDtos.CreateSubmissionRequest;
import com.mathematics.practice.PracticeDtos.SubmissionDetail;
import com.mathematics.practice.PracticeDtos.SubmissionResult;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/submissions")
public class SubmissionController {

    private final PracticeService practiceService;

    public SubmissionController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    /**
     * Idempotency-Key 由前端在进入题目时生成：重试复用同一个值，重新作答换新值。
     */
    @PostMapping
    public SubmissionResult submit(CurrentUser me,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                   @Valid @RequestBody CreateSubmissionRequest request) {
        return practiceService.submit(me.requireId(), idempotencyKey, request);
    }

    @GetMapping("/{id}")
    public SubmissionDetail detail(CurrentUser me, @PathVariable long id) {
        return practiceService.detail(me.requireId(), id);
    }
}
