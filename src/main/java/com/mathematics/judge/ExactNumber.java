package com.mathematics.judge;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数值题的答案值：分子分母都是整数的精确有理数。
 *
 * <p>奥数答案大量是分数，{@code 1/3} 换成小数就永远不精确，所以判等走交叉相乘，
 * 只有配置了容差才退回到小数比较。接受的写法：
 * <ul>
 *   <li>整数与小数：{@code 12}、{@code -2.5}</li>
 *   <li>分数：{@code 3/4}、{@code -3/4}</li>
 *   <li>带分数：{@code 1 1/2}、{@code 1又1/2}（负号作用于整体，{@code -2 1/3} 是 -7/3）</li>
 *   <li>百分数：{@code 50%}</li>
 * </ul>
 * 全角数字与符号先折成半角，小学生用中文输入法很容易打出全角。
 */
public record ExactNumber(BigInteger numerator, BigInteger denominator) {

    private static final MathContext DECIMAL = MathContext.DECIMAL128;

    private static final Pattern DECIMAL_NUMBER = Pattern.compile("([+-]?)(\\d+(?:\\.\\d+)?|\\.\\d+)(%?)");
    private static final Pattern FRACTION = Pattern.compile("([+-]?)(?:(\\d+)(?:\\s+|又))?(\\d+)/(\\d+)");

    public ExactNumber {
        if (denominator.signum() == 0) {
            throw new GraderException("denominator is zero");
        }
        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        BigInteger gcd = numerator.gcd(denominator);
        if (gcd.signum() != 0 && !gcd.equals(BigInteger.ONE)) {
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
        }
    }

    public static ExactNumber parse(String raw) {
        if (raw == null) {
            throw new GraderException("value is not a number");
        }
        String text = normalize(raw);
        Matcher fraction = FRACTION.matcher(text);
        if (fraction.matches()) {
            BigInteger whole = fraction.group(2) == null ? BigInteger.ZERO : new BigInteger(fraction.group(2));
            BigInteger num = new BigInteger(fraction.group(3));
            BigInteger den = new BigInteger(fraction.group(4));
            if (den.signum() == 0) {
                throw new GraderException("value is not a number");
            }
            ExactNumber value = new ExactNumber(whole.multiply(den).add(num), den);
            return "-".equals(fraction.group(1)) ? value.negate() : value;
        }
        Matcher decimal = DECIMAL_NUMBER.matcher(text);
        if (decimal.matches()) {
            BigDecimal value = new BigDecimal(decimal.group(2));
            if ("-".equals(decimal.group(1))) {
                value = value.negate();
            }
            if (!decimal.group(3).isEmpty()) {
                value = value.movePointLeft(2);
            }
            return of(value);
        }
        throw new GraderException("value is not a number");
    }

    public static ExactNumber of(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        int scale = Math.max(stripped.scale(), 0);
        return new ExactNumber(stripped.movePointRight(scale).toBigIntegerExact(), BigInteger.TEN.pow(scale));
    }

    public ExactNumber negate() {
        return new ExactNumber(numerator.negate(), denominator);
    }

    public boolean sameValue(ExactNumber other) {
        return numerator.multiply(other.denominator).equals(other.numerator.multiply(denominator));
    }

    public BigDecimal toDecimal() {
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), DECIMAL);
    }

    /** 全角转半角、去掉首尾空白、把连续空白压成一个，带分数的空格写法才认得出来。 */
    private static String normalize(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (char ch : raw.toCharArray()) {
            if (ch >= '\uFF01' && ch <= '\uFF5E') {
                out.append((char) (ch - 0xFEE0));
            } else if (ch == '\u3000') {
                out.append(' ');
            } else {
                out.append(ch);
            }
        }
        return out.toString().trim().replaceAll("\\s+", " ");
    }
}
