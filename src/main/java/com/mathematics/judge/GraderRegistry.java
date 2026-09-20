package com.mathematics.judge;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mathematics.judge.impl.BlankGrader;
import com.mathematics.judge.impl.JudgeGrader;
import com.mathematics.judge.impl.MultiChoiceGrader;
import com.mathematics.judge.impl.NumericGrader;
import com.mathematics.judge.impl.SingleChoiceGrader;

public final class GraderRegistry {

    private final Map<ProblemType, Grader> graders = new EnumMap<>(ProblemType.class);

    public GraderRegistry(List<Grader> graders) {
        for (Grader grader : graders) {
            Grader previous = this.graders.putIfAbsent(grader.supports(), grader);
            if (previous != null) {
                throw new IllegalStateException("duplicate grader for " + grader.supports());
            }
        }
    }

    public static GraderRegistry defaults() {
        return new GraderRegistry(List.of(
                new SingleChoiceGrader(),
                new MultiChoiceGrader(),
                new JudgeGrader(),
                new NumericGrader(),
                new BlankGrader()
        ));
    }

    public GradeResult grade(ProblemType type, GradeRequest request) {
        Grader grader = graders.get(type);
        if (grader == null) {
            throw new GraderException("no grader registered for " + type);
        }
        return grader.grade(request);
    }
}
