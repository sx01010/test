package com.mathematics.judge;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

public final class GraderConfig {

    private final BigDecimal tolerance;
    private final boolean orderIndependent;

    public GraderConfig(BigDecimal tolerance, boolean orderIndependent) {
        this.tolerance = tolerance;
        this.orderIndependent = orderIndependent;
    }

    public static GraderConfig from(JsonNode node) {
        BigDecimal tolerance = BigDecimal.ZERO;
        boolean orderIndependent = false;
        if (node != null && !node.isNull() && node.isObject()) {
            if (node.hasNonNull("tolerance")) {
                try {
                    tolerance = new BigDecimal(node.get("tolerance").asText());
                } catch (NumberFormatException ex) {
                    throw new GraderException("graderConfig.tolerance is not a number", ex);
                }
                if (tolerance.signum() < 0) {
                    throw new GraderException("graderConfig.tolerance must be >= 0");
                }
            }
            if (node.hasNonNull("orderIndependent")) {
                orderIndependent = node.get("orderIndependent").asBoolean();
            }
        }
        return new GraderConfig(tolerance, orderIndependent);
    }

    public BigDecimal tolerance() {
        return tolerance;
    }

    public boolean orderIndependent() {
        return orderIndependent;
    }
}
