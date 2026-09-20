package com.mathematics.quality;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class QualityDtos {

    private QualityDtos() {
    }

    public record CreateFeedbackRequest(
            @NotBlank String reason,
            @Size(max = 500, message = "补充说明最长 500 字") String detail) {
    }

    /**
     * @param reused 命中同一人同题未结案的工单时为 true，不会重复建单
     */
    public record FeedbackCreated(long feedbackId, String status, boolean reused) {
    }
}
