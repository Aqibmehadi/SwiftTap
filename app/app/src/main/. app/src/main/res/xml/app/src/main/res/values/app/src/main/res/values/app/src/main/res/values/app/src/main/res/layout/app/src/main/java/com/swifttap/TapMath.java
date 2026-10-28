package com.swifttap;

final class TapMath {
    static final int MIN_TPS = 1, MAX_TPS = 1000;
    static final int MIN_HOLD = 1, MAX_HOLD = 200;

    private TapMath() {}

    static int clampTps(int v) {
        return Math.max(MIN_TPS, Math.min(MAX_TPS, v));
    }

    static int clampHold(int v) {
        return Math.max(MIN_HOLD, Math.min(MAX_HOLD, v));
    }

    static long intervalNanos(int tps) {
        return 1_000_000_000L / clampTps(tps);
    }

    static int nextIndex(int current, int count, boolean sequential) {
        if (!sequential || count <= 1) return 0;
        return (current + 1) % count;
    }

    static double actualTps(long completed, long activeMs) {
        return activeMs <= 0 ? 0.0 : completed * 1000.0 / activeMs;
    }

    static long missedIntervals(long lateNanos, long intervalNanos) {
        return lateNanos <= 0 ? 0 : lateNanos / intervalNanos;
    }
}
