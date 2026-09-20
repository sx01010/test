package com.mathematics.judge;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

public final class JsonAnswers {

    private JsonAnswers() {
    }

    public static JsonNode requireObject(JsonNode node, String name) {
        if (node == null || node.isNull() || !node.isObject()) {
            throw new GraderException(name + " must be a JSON object");
        }
        return node;
    }

    public static String requiredText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            throw new GraderException("missing field: " + field);
        }
        String text = value.isTextual() ? value.asText() : value.asText();
        if (text == null || text.isBlank()) {
            throw new GraderException("field " + field + " must not be blank");
        }
        return text.trim();
    }

    public static String choice(JsonNode object) {
        return requiredText(object, "choice").toUpperCase(Locale.ROOT);
    }

    public static Set<String> choices(JsonNode object) {
        JsonNode value = object.get("choices");
        if (value == null || !value.isArray() || value.isEmpty()) {
            throw new GraderException("choices must be a non-empty array");
        }
        Set<String> result = new LinkedHashSet<>();
        for (JsonNode item : value) {
            if (item == null || item.isNull() || item.asText().isBlank()) {
                throw new GraderException("choices must not contain blank values");
            }
            result.add(item.asText().trim().toUpperCase(Locale.ROOT));
        }
        return result;
    }

    public static boolean judgeValue(JsonNode object) {
        JsonNode value = object.get("value");
        if (value == null || value.isNull()) {
            throw new GraderException("missing field: value");
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        String text = value.asText().trim().toLowerCase(Locale.ROOT);
        if ("true".equals(text) || "t".equals(text) || "1".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "f".equals(text) || "0".equals(text)) {
            return false;
        }
        throw new GraderException("value is not a boolean");
    }

    public static BigDecimal numericValue(JsonNode object) {
        JsonNode value = object.get("value");
        if (value == null || value.isNull()) {
            throw new GraderException("missing field: value");
        }
        try {
            return new BigDecimal(value.isNumber() ? value.numberValue().toString() : value.asText().trim());
        } catch (NumberFormatException ex) {
            throw new GraderException("value is not a number", ex);
        }
    }

    public static List<String> userBlanks(JsonNode object) {
        JsonNode value = object.get("blanks");
        if (value == null || !value.isArray()) {
            throw new GraderException("blanks must be an array");
        }
        List<String> blanks = new ArrayList<>();
        for (JsonNode item : value) {
            blanks.add(item == null || item.isNull() ? "" : item.asText().trim());
        }
        return blanks;
    }

    public static List<List<String>> standardBlanks(JsonNode object) {
        JsonNode value = object.get("blanks");
        if (value == null || !value.isArray() || value.isEmpty()) {
            throw new GraderException("standard blanks must be a non-empty array");
        }
        List<List<String>> blanks = new ArrayList<>();
        for (JsonNode item : value) {
            List<String> aliases = new ArrayList<>();
            if (item != null && item.isArray()) {
                for (JsonNode alias : item) {
                    if (alias != null && !alias.isNull() && !alias.asText().isBlank()) {
                        aliases.add(alias.asText().trim());
                    }
                }
            } else if (item != null && !item.isNull() && !item.asText().isBlank()) {
                aliases.add(item.asText().trim());
            }
            if (aliases.isEmpty()) {
                throw new GraderException("each standard blank needs at least one acceptable answer");
            }
            blanks.add(List.copyOf(aliases));
        }
        return blanks;
    }
}
