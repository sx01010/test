package com.mathematics.judge.impl;

import java.math.BigDecimal;

import com.mathematics.judge.ExactNumber;
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

    /**
     * 容差为 0 时按有理数精确判等，{@code 0.3333} 不等于 {@code 1/3}；
     * 配了容差才换成小数比较差值。
     */
    @Override
    public GradeResult grade(GradeRequest request) {
        ExactNumber user = JsonAnswers.numericValue(JsonAnswers.requireObject(request.userAnswer(), "userAnswer"));
        ExactNumber standard = JsonAnswers.numericValue(JsonAnswers.requireObject(request.standardAnswer(), "standardAnswer"));
        GraderConfig config = GraderConfig.from(request.graderConfig());
        boolean correct = config.tolerance().signum() == 0
                ? user.sameValue(standard)
                : user.toDecimal().subtract(standard.toDecimal()).abs().compareTo(config.tolerance()) <= 0;
        BigDecimal max = BigDecimal.valueOf(request.maxScore());
        return GradeResult.of(correct ? GradeOutcome.CORRECT : GradeOutcome.WRONG, correct ? max : BigDecimal.ZERO, max, null);
    }
}
