package com.dex.boxshopkeeper.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An amount of one currency paid when a trade runs (update 01, §1). Only the three known types
 * are ever held here; an unknown type in a shop file disables the trade instead.
 */
public record CurrencyCost(String type, long amount) {

    public static final String GEM = "gem";
    public static final String POINTS = "points";
    public static final String VAULT = "vault";
    /** Fixed display and payment order. */
    public static final List<String> ORDER = List.of(GEM, POINTS, VAULT);

    private static final long TRILLION = 1_000_000_000_000L;

    public static boolean known(String type) {
        return ORDER.contains(type);
    }

    /** Largest amount one cost of this type may have, or -1 for an unknown type. */
    public static long maxAmount(String type) {
        if (type == null) {
            return -1;
        }
        return switch (type) {
            case VAULT, GEM -> TRILLION;
            case POINTS -> Integer.MAX_VALUE;
            default -> -1;
        };
    }

    /** Position in {@link #ORDER}; unknown types sort last. */
    public static int rank(String type) {
        int index = ORDER.indexOf(type);
        return index < 0 ? ORDER.size() : index;
    }

    /** Thrown when costs of one type add up to more than its maximum. */
    public static final class TooLargeException extends RuntimeException {
        private final String type;

        public TooLargeException(String type) {
            super("sum of " + type + " costs exceeds " + maxAmount(type));
            this.type = type;
        }

        public String type() {
            return type;
        }
    }

    /**
     * One cost per type, amounts summed exactly, in the fixed order.
     * @throws TooLargeException when a sum exceeds the type's maximum (never capped)
     */
    public static List<CurrencyCost> merge(List<CurrencyCost> costs) {
        Map<String, Long> sums = new LinkedHashMap<>();
        for (CurrencyCost cost : costs) {
            long sum;
            try {
                sum = Math.addExact(sums.getOrDefault(cost.type(), 0L), cost.amount());
            } catch (ArithmeticException e) {
                throw new TooLargeException(cost.type());
            }
            if (sum > maxAmount(cost.type())) {
                throw new TooLargeException(cost.type());
            }
            sums.put(cost.type(), sum);
        }
        List<CurrencyCost> merged = new ArrayList<>(sums.size());
        sums.forEach((type, amount) -> merged.add(new CurrencyCost(type, amount)));
        merged.sort(Comparator.comparingInt(cost -> rank(cost.type())));
        return merged;
    }

    /**
     * A stored cost amount: an integer {@link Number} in {@code 1..maxAmount(type)}, never clamped.
     * @return the amount, or -1 when the value is missing, not an integer or out of range
     */
    public static long parseAmount(Object raw, String type) {
        long max = maxAmount(type);
        long value;
        if (raw instanceof Integer || raw instanceof Long || raw instanceof Short || raw instanceof Byte) {
            value = ((Number) raw).longValue();
        } else if (raw instanceof BigInteger big && big.bitLength() < Long.SIZE) {
            value = big.longValue();
        } else {
            return -1;
        }
        return value >= 1 && value <= max ? value : -1;
    }
}
