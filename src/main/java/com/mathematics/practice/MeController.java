package com.mathematics.practice;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mathematics.guard.RequireLogin;
import com.mathematics.identity.CurrentUser;
import com.mathematics.practice.PracticeDtos.MarkMasteredRequest;
import com.mathematics.practice.PracticeDtos.MarkMasteredResponse;
import com.mathematics.practice.PracticeDtos.SubmissionSummary;
import com.mathematics.practice.PracticeDtos.TagProgress;
import com.mathematics.practice.PracticeDtos.WrongItem;
import com.mathematics.support.CursorPage;

import jakarta.validation.Valid;

@RestController
@RequireLogin
@RequestMapping("/api/v1/me")
public class MeController {

    private final PracticeService practiceService;

    public MeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    @GetMapping("/submissions")
    public CursorPage<SubmissionSummary> submissions(CurrentUser me,
                                                    @RequestParam(required = false) Long problemId,
                                                    @RequestParam(required = false) String cursor,
                                                    @RequestParam(required = false) Integer limit) {
        return practiceService.listMine(me.requireId(), problemId, cursor, limit);
    }

    /**
     * mastered 默认 0：R13 要求错题本默认只看未掌握，传 1 看已掌握的。
     */
    @GetMapping("/wrong-items")
    public CursorPage<WrongItem> wrongItems(CurrentUser me,
                                           @RequestParam(required = false, defaultValue = "0") Integer mastered,
                                           @RequestParam(required = false) Long tagId,
                                           @RequestParam(required = false) String since,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(required = false) Integer limit) {
        return practiceService.listWrongItems(me.requireId(), mastered, tagId, since, cursor, limit);
    }

    @PatchMapping("/wrong-items/{problemId}")
    public MarkMasteredResponse markMastered(CurrentUser me, @PathVariable long problemId,
                                            @Valid @RequestBody MarkMasteredRequest request) {
        return practiceService.markMastered(me.requireId(), problemId, request.mastered());
    }

    @GetMapping("/progress")
    public List<TagProgress> progress(CurrentUser me) {
        return practiceService.progress(me.requireId());
    }
}
