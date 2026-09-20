package com.mathematics.judge;

public interface Grader {

    ProblemType supports();

    GradeResult grade(GradeRequest request);
}
