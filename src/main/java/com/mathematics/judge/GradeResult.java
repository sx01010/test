package com.mathematics.judge;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public final class GradeResult {

    private final GradeOutcome result;
    private final BigDecimal score;
    private final BigDecimal maxScore;
    private final JsonNode details;

    public GradeResult(GradeOutcome result, BigDecimal score, BigDecimal maxScore, JsonNode details) {
        this.result = result;
        this.score = score;
        this.maxScore = maxScore;
        this.details = details == null ? JsonNodeFactory.instance.objectNode() : details;
    }

    public static GradeResult of(GradeOutcome result, BigDecimal score, BigDecimal maxScore, JsonNode details) {
        return new GradeResult(result, score, maxScore, details);
    }

    public GradeOutcome result() {
        return result;
    }

    public BigDecimal score() {
        return score;
    }

    public BigDecimal maxScore() {
        return maxScore;
    }

    public JsonNode details() {
        return details;
    }
}
