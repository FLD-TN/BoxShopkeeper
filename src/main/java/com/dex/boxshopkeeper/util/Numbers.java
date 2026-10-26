package com.dex.boxshopkeeper.util;

/** Number formatting shown to players (§8.4 of update 01). Pure. */
public final class Numbers {

    private Numbers() {
    }

    /** Groups thousands with {@code .}: {@code 50000 → "50.000"}, {@code 1250000 → "1.250.000"}. */
    public static String group(long value) {
        String digits = Long.toString(value);
        int start = value < 0 ? 1 : 0;
        int length = digits.length() - start;
        if (length <= 3) {
            return digits;
        }
        StringBuilder out = new StringBuilder(digits.length() + length / 3);
        out.append(digits, 0, start);
        int first = length % 3 == 0 ? 3 : length % 3;
        out.append(digits, start, start + first);
        for (int i = start + first; i < digits.length(); i += 3) {
            out.append('.').append(digits, i, i + 3);
        }
        return out.toString();
    }
}
