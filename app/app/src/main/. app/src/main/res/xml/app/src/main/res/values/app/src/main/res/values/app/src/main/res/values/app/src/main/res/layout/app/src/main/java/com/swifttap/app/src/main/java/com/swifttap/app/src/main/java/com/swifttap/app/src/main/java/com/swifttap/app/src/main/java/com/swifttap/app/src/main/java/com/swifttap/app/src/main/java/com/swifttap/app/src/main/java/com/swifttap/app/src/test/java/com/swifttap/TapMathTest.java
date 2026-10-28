package com.swifttap;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TapMathTest {
    @Test
    public void intervalFor10TpsIs100ms() {
        assertEquals(100_000_000L, TapMath.intervalNanos(10));
    }

    @Test
    public void intervalFor1000TpsIs1ms() {
        assertEquals(1_000_000L, TapMath.intervalNanos(1000));
    }

    @Test
    public void tpsIsClamped() {
        assertEquals(1, TapMath.clampTps(0));
        assertEquals(1000, TapMath.clampTps(5000));
        assertEquals(50, TapMath.clampTps(50));
    }

    @Test
    public void holdIsClamped() {
        assertEquals(1, TapMath.clampHold(-3));
        assertEquals(200, TapMath.clampHold(999));
    }

    @Test
    public void sequentialIndexWraps() {
        assertEquals(1, TapMath.nextIndex(0, 3, true));
        assertEquals(2, TapMath.nextIndex(1, 3, true));
        assertEquals(0, TapMath.nextIndex(2, 3, true));
    }

    @Test
    public void nonSequentialStaysOnFirstPoint() {
        assertEquals(0, TapMath.nextIndex(0, 3, false));
        assertEquals(0, TapMath.nextIndex(2, 1, true));
    }

    @Test
    public void actualTpsMath() {
        assertEquals(250.0, TapMath.actualTps(500, 2000), 0.001);
        assertEquals(0.0, TapMath.actualTps(10, 0), 0.001);
    }

    @Test
    public void missedIntervalsMath() {
        assertEquals(3, TapMath.missedIntervals(350_000_000L, 100_000_000L));
        assertEquals(0, TapMath.missedIntervals(-5L, 100_000_000L));
    }
}
