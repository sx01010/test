package com.mathematics.judge.impl;

import java.math.BigDecimal;

import com.mathematics.judge.GradeOutcome;
import com.mathematics.judge.GradeRequest;
import com.mathematics.judge.GradeResult;
import com.mathematics.judge.Grader;
import com.mathematics.judge.JsonAnswers;
import com.mathematics.judge.ProblemType;

public class JudgeGrader implements Grader {

    @Override
    public ProblemType supports() {
        return ProblemType.JUDGE;
    }

    @Override
    public GradeResult grade(GradeRequest request) {
        boolean user = JsonAnswers.judgeValue(JsonAnswers.requireObject(request.userAnswer(), "userAnswer"));
        boolean standard = JsonAnswers.judgeValue(JsonAnswers.requireObject(request.standardAnswer(), "standardAnswer"));
        boolean correct = user == standard;
        BigDecimal max = BigDecimal.valueOf(request.maxScore());
        return GradeResult.of(correct ? GradeOutcome.CORRECT : GradeOutcome.WRONG, correct ? max : BigDecimal.ZERO, max, null);
    }
}
