package com.mathematics.practice;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class PracticeDtos {

    private PracticeDtos() {
    }

    public record CreateSubmissionRequest(
            @NotNull Long problemId,
            @NotNull Long problemVersionId,
            @NotNull JsonNode answer,
            @Min(0) Integer durationMs) {
    }

    public record SubmissionResult(
            long id,
            String result,
            BigDecimal score,
            BigDecimal maxScore,
            JsonNode details) {
    }

    public record SubmissionDetail(
            long id,
            long problemId,
            long problemVersionId,
            int versionNo,
            boolean versionChanged,
            String result,
            BigDecimal score,
            BigDecimal maxScore,
            JsonNode details,
            JsonNode answer,
            Integer durationMs,
            LocalDateTime createdAt) {
    }

    public record SubmissionSummary(
            long id,
            long problemId,
            String title,
            int versionNo,
            boolean versionChanged,
            String result,
            BigDecimal score,
            BigDecimal maxScore,
            Integer durationMs,
            LocalDateTime createdAt) {
    }

    public record WrongItem(
            long problemId,
            String title,
            boolean mastered,
            int wrongCount,
            int consecutiveCorrect,
            LocalDateTime lastWrongAt,
            boolean versionChanged,
            long lastVersionId) {
    }

    public record MarkMasteredRequest(@NotNull Boolean mastered) {
    }

    public record MarkMasteredResponse(long problemId, boolean mastered) {
    }

    public record TagProgress(
            long tagId,
            String tagName,
            int attemptCount,
            int correctCount,
            LocalDateTime lastSubmittedAt) {
    }
}
