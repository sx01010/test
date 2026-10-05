package com.mathematics.judge.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathematics.judge.ExactNumber;
import com.mathematics.judge.GradeOutcome;
import com.mathematics.judge.GradeRequest;
import com.mathematics.judge.GradeResult;
import com.mathematics.judge.Grader;
import com.mathematics.judge.GraderConfig;
import com.mathematics.judge.GraderException;
import com.mathematics.judge.JsonAnswers;
import com.mathematics.judge.ProblemType;

public class BlankGrader implements Grader {

    @Override
    public ProblemType supports() {
        return ProblemType.BLANK;
    }

    @Override
    public GradeResult grade(GradeRequest request) {
        List<String> userBlanks = JsonAnswers.userBlanks(JsonAnswers.requireObject(request.userAnswer(), "userAnswer"));
        List<List<String>> standardBlanks = JsonAnswers.standardBlanks(JsonAnswers.requireObject(request.standardAnswer(), "standardAnswer"));
        GraderConfig config = GraderConfig.from(request.graderConfig());

        boolean[] correctFlags = config.orderIndependent()
                ? matchOrderIndependent(userBlanks, standardBlanks)
                : matchInOrder(userBlanks, standardBlanks);

        int correctCount = 0;
        ArrayNode details = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < standardBlanks.size(); i++) {
            boolean correct = correctFlags[i];
            if (correct) {
                correctCount++;
            }
            ObjectNode item = JsonNodeFactory.instance.objectNode();
            item.put("index", i);
            item.put("correct", correct);
            details.add(item);
        }

        BigDecimal max = BigDecimal.valueOf(request.maxScore());
        BigDecimal score = max.multiply(BigDecimal.valueOf(correctCount))
                .divide(BigDecimal.valueOf(standardBlanks.size()), 2, RoundingMode.HALF_UP);
        GradeOutcome outcome;
        if (correctCount == standardBlanks.size()) {
            outcome = GradeOutcome.CORRECT;
        } else if (correctCount == 0) {
            outcome = GradeOutcome.WRONG;
        } else {
            outcome = GradeOutcome.PARTIAL;
        }
        ObjectNode detailObject = JsonNodeFactory.instance.objectNode();
        detailObject.set("blanks", details);
        return GradeResult.of(outcome, score, max, detailObject);
    }

    private static boolean[] matchInOrder(List<String> userBlanks, List<List<String>> standardBlanks) {
        boolean[] flags = new boolean[standardBlanks.size()];
        for (int i = 0; i < standardBlanks.size(); i++) {
            String user = i < userBlanks.size() ? userBlanks.get(i) : "";
            flags[i] = matchesAlias(user, standardBlanks.get(i));
        }
        return flags;
    }

    private static boolean[] matchOrderIndependent(List<String> userBlanks, List<List<String>> standardBlanks) {
        boolean[] flags = new boolean[standardBlanks.size()];
        boolean[] used = new boolean[standardBlanks.size()];
        for (String user : userBlanks) {
            for (int i = 0; i < standardBlanks.size(); i++) {
                if (!used[i] && matchesAlias(user, standardBlanks.get(i))) {
                    used[i] = true;
                    flags[i] = true;
                    break;
                }
            }
        }
        return flags;
    }

    /**
     * 先比字面，再比数值：标准答案写 {@code 1/2}，学生填 {@code 0.5} 也算对。
     * 不是数字的写法（如「十二」）只能靠录入时列出别名。
     */
    private static boolean matchesAlias(String user, List<String> aliases) {
        if (user == null || user.isBlank()) {
            return false;
        }
        ExactNumber userNumber = tryParse(user);
        for (String alias : aliases) {
            if (alias.equals(user)) {
                return true;
            }
            if (userNumber != null) {
                ExactNumber aliasNumber = tryParse(alias);
                if (aliasNumber != null && aliasNumber.sameValue(userNumber)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ExactNumber tryParse(String text) {
        try {
            return ExactNumber.parse(text);
        } catch (GraderException ex) {
            return null;
        }
    }
}
