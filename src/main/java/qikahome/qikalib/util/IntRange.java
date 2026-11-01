package qikahome.qikalib.util;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.PrimitiveCodec;

public final class IntRange {
    private static final java.util.regex.Pattern RANGE_PATTERN = java.util.regex.Pattern.compile(
            "^\\s*" +
                    "(\\[|\\()" +
                    "\\s*" +
                    "([-+]?\\d*)" +
                    "\\s*,\\s*" +
                    "([-+]?\\d*)" +
                    "\\s*" +
                    "(\\]|\\))" +
                    "\\s*$");

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
            return "QikaLib.util.IntRange";
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
        var matcher = RANGE_PATTERN.matcher(rangeStr);
        if (!matcher.matches()) {
            if (rangeStr.trim().equals("*")) {
                return new IntRange(Integer.MIN_VALUE, Integer.MAX_VALUE, true, true);
            }
            throw new IllegalArgumentException("Invalid range format: " + rangeStr);
        }

        boolean minInclusive = matcher.group(1).equals("[");
        boolean maxInclusive = matcher.group(4).equals("]");

        String minStr = matcher.group(2);
        String maxStr = matcher.group(3);

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

    @Override
    public String toString() {
        return (minInclusive ? "[" : "(") +
                (min == Integer.MIN_VALUE ? "" : min) + "," +
                (max == Integer.MAX_VALUE ? "" : max) +
                (maxInclusive ? "]" : ")");
    }
}
