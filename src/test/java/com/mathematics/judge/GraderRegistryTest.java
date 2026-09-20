package com.mathematics.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class GraderRegistryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GraderRegistry registry;

    @BeforeEach
    void setUp() {
        registry = GraderRegistry.defaults();
    }

    @ParameterizedTest
    @MethodSource("singleCases")
    void singleChoice(String user, String standard, GradeOutcome expected, String score) {
        GradeResult result = grade(ProblemType.SINGLE, user, standard, null);
        assertEquals(expected, result.result());
        assertEquals(new BigDecimal(score), result.score());
    }

    static Stream<Arguments> singleCases() {
        return Stream.of(
                Arguments.of("{\"choice\":\"A\"}", "{\"choice\":\"A\"}", GradeOutcome.CORRECT, "100"),
                Arguments.of("{\"choice\":\"a\"}", "{\"choice\":\"A\"}", GradeOutcome.CORRECT, "100"),
                Arguments.of("{\"choice\":\"B\"}", "{\"choice\":\"A\"}", GradeOutcome.WRONG, "0")
        );
    }

    @ParameterizedTest
    @MethodSource("multiCases")
    void multiChoice(String user, String standard, GradeOutcome expected) {
        assertEquals(expected, grade(ProblemType.MULTI, user, standard, null).result());
    }

    static Stream<Arguments> multiCases() {
        return Stream.of(
                Arguments.of("{\"choices\":[\"A\",\"C\"]}", "{\"choices\":[\"C\",\"A\"]}", GradeOutcome.CORRECT),
                Arguments.of("{\"choices\":[\"A\",\"B\"]}", "{\"choices\":[\"A\",\"C\"]}", GradeOutcome.WRONG),
                Arguments.of("{\"choices\":[\"A\"]}", "{\"choices\":[\"A\",\"C\"]}", GradeOutcome.WRONG)
        );
    }

    @ParameterizedTest
    @MethodSource("judgeCases")
    void judge(String user, String standard, GradeOutcome expected) {
        assertEquals(expected, grade(ProblemType.JUDGE, user, standard, null).result());
    }

    static Stream<Arguments> judgeCases() {
        return Stream.of(
                Arguments.of("{\"value\":true}", "{\"value\":true}", GradeOutcome.CORRECT),
                Arguments.of("{\"value\":\"T\"}", "{\"value\":true}", GradeOutcome.CORRECT),
                Arguments.of("{\"value\":false}", "{\"value\":true}", GradeOutcome.WRONG)
        );
    }

    @ParameterizedTest
    @MethodSource("numericCases")
    void numeric(String user, String standard, String config, GradeOutcome expected) {
        assertEquals(expected, grade(ProblemType.NUMERIC, user, standard, config).result());
    }

    static Stream<Arguments> numericCases() {
        return Stream.of(
                Arguments.of("{\"value\":\"3.14\"}", "{\"value\":\"3.14\"}", "{}", GradeOutcome.CORRECT),
                Arguments.of("{\"value\":3.141}", "{\"value\":\"3.14\"}", "{\"tolerance\":0.01}", GradeOutcome.CORRECT),
                Arguments.of("{\"value\":\"3.20\"}", "{\"value\":\"3.14\"}", "{\"tolerance\":0.01}", GradeOutcome.WRONG)
        );
    }

    @ParameterizedTest
    @MethodSource("blankCases")
    void blanks(String user, String standard, String config, GradeOutcome expected, String score) {
        GradeResult result = grade(ProblemType.BLANK, user, standard, config);
        assertEquals(expected, result.result());
        assertEquals(new BigDecimal(score), result.score());
        assertTrue(result.details().has("blanks"));
    }

    static Stream<Arguments> blankCases() {
        return Stream.of(
                Arguments.of("{\"blanks\":[\"12\",\"24\"]}", "{\"blanks\":[[\"12\",\"十二\"],[\"24\"]]}", "{}", GradeOutcome.CORRECT, "100.00"),
                Arguments.of("{\"blanks\":[\"十二\",\"24\"]}", "{\"blanks\":[[\"12\",\"十二\"],[\"24\"]]}", "{}", GradeOutcome.CORRECT, "100.00"),
                Arguments.of("{\"blanks\":[\"12\",\"0\"]}", "{\"blanks\":[[\"12\"],[\"24\"]]}", "{}", GradeOutcome.PARTIAL, "50.00"),
                Arguments.of("{\"blanks\":[\"0\",\"0\"]}", "{\"blanks\":[[\"12\"],[\"24\"]]}", "{}", GradeOutcome.WRONG, "0.00"),
                Arguments.of("{\"blanks\":[\"24\",\"12\"]}", "{\"blanks\":[[\"12\"],[\"24\"]]}", "{\"orderIndependent\":true}", GradeOutcome.CORRECT, "100.00")
        );
    }

    @ParameterizedTest
    @MethodSource("malformedCases")
    void malformedSingleAnswerRejected(String user) {
        assertThrows(GraderException.class, () -> grade(ProblemType.SINGLE, user, "{\"choice\":\"A\"}", null));
    }

    static Stream<Arguments> malformedCases() {
        return Stream.of(Arguments.of("{}"), Arguments.of("{\"foo\":1}"), Arguments.of("null"));
    }

    private GradeResult grade(ProblemType type, String user, String standard, String config) {
        return registry.grade(type, new GradeRequest(read(user), read(standard), read(config), 100));
    }

    private static JsonNode read(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(json, ex);
        }
    }
}
