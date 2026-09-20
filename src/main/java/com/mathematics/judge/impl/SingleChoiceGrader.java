package com.mathematics.judge.impl;

import java.math.BigDecimal;

import com.mathematics.judge.GradeOutcome;
import com.mathematics.judge.GradeRequest;
import com.mathematics.judge.GradeResult;
import com.mathematics.judge.Grader;
import com.mathematics.judge.JsonAnswers;
import com.mathematics.judge.ProblemType;

public class SingleChoiceGrader implements Grader {

    @Override
    public ProblemType supports() {
        return ProblemType.SINGLE;
    }

    @Override
    public GradeResult grade(GradeRequest request) {
        String user = JsonAnswers.choice(JsonAnswers.requireObject(request.userAnswer(), "userAnswer"));
        String standard = JsonAnswers.choice(JsonAnswers.requireObject(request.standardAnswer(), "standardAnswer"));
        boolean correct = user.equals(standard);
        BigDecimal max = BigDecimal.valueOf(request.maxScore());
        return GradeResult.of(correct ? GradeOutcome.CORRECT : GradeOutcome.WRONG, correct ? max : BigDecimal.ZERO, max, null);
    }
}
