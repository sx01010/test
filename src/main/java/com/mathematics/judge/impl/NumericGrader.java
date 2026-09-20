package com.mathematics.judge.impl;

import java.math.BigDecimal;

import com.mathematics.judge.GradeOutcome;
import com.mathematics.judge.GradeRequest;
import com.mathematics.judge.GradeResult;
import com.mathematics.judge.Grader;
import com.mathematics.judge.GraderConfig;
import com.mathematics.judge.JsonAnswers;
import com.mathematics.judge.ProblemType;

public class NumericGrader implements Grader {

    @Override
    public ProblemType supports() {
        return ProblemType.NUMERIC;
    }

    @Override
    public GradeResult grade(GradeRequest request) {
        BigDecimal user = JsonAnswers.numericValue(JsonAnswers.requireObject(request.userAnswer(), "userAnswer"));
        BigDecimal standard = JsonAnswers.numericValue(JsonAnswers.requireObject(request.standardAnswer(), "standardAnswer"));
        GraderConfig config = GraderConfig.from(request.graderConfig());
        boolean correct = user.subtract(standard).abs().compareTo(config.tolerance()) <= 0;
        BigDecimal max = BigDecimal.valueOf(request.maxScore());
        return GradeResult.of(correct ? GradeOutcome.CORRECT : GradeOutcome.WRONG, correct ? max : BigDecimal.ZERO, max, null);
    }
}
