package com.mathematics.judge;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 一次判题输入。答案 JSON 约定：
 * <ul>
 *   <li>SINGLE：{@code {"choice":"A"}}</li>
 *   <li>MULTI：{@code {"choices":["A","C"]}}</li>
 *   <li>JUDGE：{@code {"value":true}}</li>
 *   <li>NUMERIC：{@code {"value":"3.14"}}，config 可带 {@code tolerance}</li>
 *   <li>BLANK：用户 {@code {"blanks":["12","24"]}}；
 *       标准答案每个空可以有多个可接受写法 {@code {"blanks":[["12","十二"],["24"]]}}；
 *       config 可带 {@code orderIndependent}</li>
 * </ul>
 */
public final class GradeRequest {

    private final JsonNode userAnswer;
    private final JsonNode standardAnswer;
    private final JsonNode graderConfig;
    private final int maxScore;

    public GradeRequest(JsonNode userAnswer, JsonNode standardAnswer, JsonNode graderConfig, int maxScore) {
        if (maxScore <= 0) {
            throw new IllegalArgumentException("maxScore must be positive");
        }
        this.userAnswer = userAnswer;
        this.standardAnswer = standardAnswer;
        this.graderConfig = graderConfig;
        this.maxScore = maxScore;
    }

    public JsonNode userAnswer() {
        return userAnswer;
    }

    public JsonNode standardAnswer() {
        return standardAnswer;
    }

    public JsonNode graderConfig() {
        return graderConfig;
    }

    public int maxScore() {
        return maxScore;
    }
}
