package qikahome.qikalib.util;

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.PrimitiveCodec;

/**
 * 整数区间解析与判定。
 *
 * <p>支持 {@code [a,b]} {@code [a,b)} {@code (a,b]} {@code (a,b)} 与 Minecraft 风格的
 * {@code a..b} / {@code ..b} / {@code a..} / {@code *}；空端点表示无界。
 */
public final class IntRange implements Predicate<Integer> {
    private static final Pattern RANGE_PATTERN = Pattern.compile(
            "^\\s*" + // 开头的空白
                    "(\\[|\\()" + // 左括号类型 (group 1)
                    "\\s*" +
                    "([-+]?\\d*)" + // 最小值 (group 2)，可以为空或带正负号
                    "\\s*,\\s*" +
                    "([-+]?\\d*)" + // 最大值 (group 3)，可以为空或带正负号
                    "\\s*" +
                    "(\\]|\\))" + // 右括号类型 (group 4)
                    "\\s*$" // 结尾的空白
    );

    /** JSON 里既接受数字（精确值），也接受区间字符串。 */
    public static final PrimitiveCodec<IntRange> CODEC = new PrimitiveCodec<IntRange>() {
        @Override
        public <T> DataResult<IntRange> read(final DynamicOps<T> ops, final T input) {
            DataResult<String> asString = ops.getStringValue(input);
            if (asString.result().isPresent()) {
                return asString.map(IntRange::parse);
            }
            return ops.getNumberValue(input).flatMap(num -> {
                int v = num.intValue();
                return DataResult.success(new IntRange(v, v, true, true));
            });
        }

        @Override
        public <T> T write(final DynamicOps<T> ops, final IntRange value) {
            return ops.createString(value.toString());
        }

        @Override
        public String toString() {
            return "QikaLib.IntRange";
        }
    };

    private final int min;
    private final int max;
    private final boolean minInclusive;
    private final boolean maxInclusive;

    private IntRange(int min, int max, boolean minInclusive, boolean maxInclusive) {
        this.min = min;
        this.max = max;
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    public static IntRange parse(String rangeStr) {
        Matcher matcher = RANGE_PATTERN.matcher(rangeStr);
        if (!matcher.matches()) {
            // Minecraft 风格范围：a..b, ..b, a.., ..
            String trimmed = rangeStr.trim();
            if (trimmed.contains("..")) {
                String[] parts = trimmed.split("\\.\\.", -1);
                String minStr = parts[0].trim();
                String maxStr = parts.length > 1 ? parts[1].trim() : "";
                int min = minStr.isEmpty() ? Integer.MIN_VALUE : Integer.parseInt(minStr);
                int max = maxStr.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(maxStr);
                if (min > max)
                    throw new IllegalArgumentException("min > max");
                return new IntRange(min, max, true, true);
            }
            // 特殊处理通配符 "*"
            if (trimmed.equals("*")) {
                return new IntRange(Integer.MIN_VALUE, Integer.MAX_VALUE, true, true);
            }
            throw new IllegalArgumentException("Invalid range format: " + rangeStr);
        }

        boolean minInclusive = matcher.group(1).equals("[");
        boolean maxInclusive = matcher.group(4).equals("]");

        String minStr = matcher.group(2);
        String maxStr = matcher.group(3);

        // 解析最小值
        int min;
        if (minStr.isEmpty()) {
            min = Integer.MIN_VALUE;
        } else {
            try {
                min = Integer.parseInt(minStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid min value: " + minStr);
            }
        }

        // 解析最大值
        int max;
        if (maxStr.isEmpty()) {
            max = Integer.MAX_VALUE;
        } else {
            try {
                max = Integer.parseInt(maxStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid max value: " + maxStr);
            }
        }

        // 有效性检查
        if (min > max) {
            throw new IllegalArgumentException("min > max");
        }
        if (min == max && (!minInclusive || !maxInclusive)) {
            throw new IllegalArgumentException("Empty range: single point but not closed on both ends");
        }

        return new IntRange(min, max, minInclusive, maxInclusive);
    }

    public boolean contains(int value) {
        boolean minOk = minInclusive ? (value >= min) : (value > min);
        boolean maxOk = maxInclusive ? (value <= max) : (value < max);
        return minOk && maxOk;
    }

    // getters 便于调试
    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public boolean isMinInclusive() {
        return minInclusive;
    }

    public boolean isMaxInclusive() {
        return maxInclusive;
    }

    @Override
    public String toString() {
        return (minInclusive ? "[" : "(") +
                (min == Integer.MIN_VALUE ? "" : min) + "," +
                (max == Integer.MAX_VALUE ? "" : max) +
                (maxInclusive ? "]" : ")");
    }

    @Override
    public boolean test(Integer t) {
        return contains(t);
    }
}
