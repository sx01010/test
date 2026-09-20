package com.mathematics.quality;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mathematics.identity.CurrentUser;
import com.mathematics.quality.QualityDtos.CreateFeedbackRequest;
import com.mathematics.quality.QualityDtos.FeedbackCreated;

import jakarta.validation.Valid;

@RestController
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/api/v1/problems/{id}/feedback")
    public FeedbackCreated submit(CurrentUser me, @PathVariable long id,
                                  @Valid @RequestBody CreateFeedbackRequest request) {
        return feedbackService.submit(me.requireId(), id, request);
    }
}
