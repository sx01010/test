package com.mathematics.practice;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class PracticeRows {

    private PracticeRows() {
    }

    public record Submission(
            long id,
            long userId,
            long problemId,
            long problemVersionId,
            int versionNo,
            Long currentVersionId,
            String title,
            String answerJson,
            String result,
            BigDecimal score,
            BigDecimal maxScore,
            String detailsJson,
            Integer durationMs,
            LocalDateTime createdAt) {

        public boolean versionChanged() {
            return currentVersionId != null && currentVersionId != problemVersionId;
        }
    }

    public record WrongItem(
            long id,
            long problemId,
            String title,
            boolean mastered,
            int wrongCount,
            int consecutiveCorrect,
            LocalDateTime lastWrongAt,
            long lastVersionId,
            Long currentVersionId) {

        public boolean versionChanged() {
            return currentVersionId != null && currentVersionId != lastVersionId;
        }
    }
}
