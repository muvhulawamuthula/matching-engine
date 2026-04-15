package com.muvhulawa.matchingengine.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PriceUtil {
    public static final int SCALE = 2;
    public static final long TICKS_PER_UNIT = 100L;

    private PriceUtil() {
    }

    public static long toTicks(double price) {
        return BigDecimal.valueOf(price)
                .movePointRight(SCALE)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public static String formatTicks(long ticks) {
        return BigDecimal.valueOf(ticks, SCALE).toPlainString();
    }
}
