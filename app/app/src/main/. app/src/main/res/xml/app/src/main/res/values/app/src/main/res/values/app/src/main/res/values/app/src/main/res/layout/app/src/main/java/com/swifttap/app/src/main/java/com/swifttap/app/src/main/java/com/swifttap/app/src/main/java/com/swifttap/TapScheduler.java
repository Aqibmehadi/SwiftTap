package com.swifttap;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;

import java.util.Locale;

/**
 * Single background thread, one reusable Runnable, prebuilt gestures (zero allocation per tap).
 * Next tap is sent only after Android confirms the previous one, so no unbounded queue.
 */
final class TapScheduler {
    static final int IDLE = 0, RUNNING = 1, PAUSED = 2;

    interface ErrorListener {
        void onError(String message);
    }

    private final AccessibilityService service;
    private final ErrorListener errorListener;
    private final HandlerThread thread;
    private final Handler handler;

    private volatile int state = IDLE;
    private volatile int requestedTps;
    private volatile long sent, completed, cancelled, rejected, late;
    private volatile long activeMs, runStartMs;

    // worker-thread only
    private GestureDescription[] gestures = new GestureDescription[0];
    private boolean sequential;
    private long intervalNanos = 100_000_000L;
    private long nextDue;
    private int index;
    private boolean inFlight;
    private int badStreak;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            tick();
        }
    };

    private final AccessibilityService.GestureResultCallback callback =
            new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription g) {
                    completed++;
                    badStreak = 0;
                    inFlight = false;
                    tick();
                }

                @Override
                public void onCancelled(GestureDescription g) {
                    cancelled++;
                    inFlight = false;
                    if (++badStreak >= 100) {
                        fail("Android keeps cancelling taps. Another gesture or app may be blocking.");
                    } else {
                        tick();
                    }
                }
            };

    TapScheduler(AccessibilityService s, ErrorListener l) {
        service = s;
        errorListener = l;
        thread = new HandlerThread("swifttap-scheduler", Process.THREAD_PRIORITY_DISPLAY);
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    void start(float[] xs, float[] ys, int tps, int holdMs, final boolean seq) {
        final int n = xs.length;
        if (n == 0) return;
        final GestureDescription[] built = new GestureDescription[n];
        final int hold = TapMath.clampHold(holdMs);
        for (int i = 0; i < n; i++) {
            Path p = new Path();
            p.moveTo(xs[i], ys[i]);
            built[i] = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(p, 0, hold))
                    .build();
        }
        final int t = TapMath.clampTps(tps);
        requestedTps = t;
        handler.post(new Runnable() {
            @Override
            public void run() {
                handler.removeCallbacks(tickRunnable);
                gestures = built;
                sequential = seq;
                intervalNanos = TapMath.intervalNanos(t);
                index = 0;
                badStreak = 0;
                sent = 0;
                completed = 0;
                cancelled = 0;
                rejected = 0;
                late = 0;
                activeMs = 0;
                runStartMs = SystemClock.uptimeMillis();
                nextDue = System.nanoTime();
                state = RUNNING;
                tick();
            }
        });
    }

    void pause() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (state != RUNNING) return;
                activeMs += SystemClock.uptimeMillis() - runStartMs;
                state = PAUSED;
                handler.removeCallbacks(tickRunnable);
            }
        });
    }

    void resume() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (state != PAUSED) return;
                runStartMs = SystemClock.uptimeMillis();
                nextDue = System.nanoTime();
                state = RUNNING;
                tick();
            }
        });
    }

    void stop() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (state == IDLE) return;
                if (state == RUNNING) activeMs += SystemClock.uptimeMillis() - runStartMs;
                state = IDLE;
                handler.removeCallbacks(tickRunnable);
            }
        });
    }

    void release() {
        stop();
        thread.quitSafely();
    }

    // ---- worker thread ----

    private void tick() {
        if (state != RUNNING || inFlight) return;
        long now = System.nanoTime();
        long wait = nextDue - now;
        if (wait > 0) {
            handler.removeCallbacks(tickRunnable);
            handler.postDelayed(tickRunnable, (wait + 999_999L) / 1_000_000L);
            return;
        }
        long missed = TapMath.missedIntervals(-wait, intervalNanos);
        if (missed > 0) {
            late += missed;
            nextDue = now;
        }
        nextDue += intervalNanos;
        fire();
    }

    private void fire() {
        GestureDescription g = gestures[index];
        index = TapMath.nextIndex(index, gestures.length, sequential);
        inFlight = true;
        sent++;
        boolean ok;
        try {
            ok = service.dispatchGesture(g, callback, handler);
        } catch (RuntimeException e) {
            ok = false;
        }
        if (!ok) {
            inFlight = false;
            rejected++;
            if (++badStreak >= 20) {
                fail("Android rejected the taps. Is the accessibility service still enabled?");
            } else {
                handler.postDelayed(tickRunnable, 5);
            }
        }
    }

    private void fail(String msg) {
        if (state == RUNNING) activeMs += SystemClock.uptimeMillis() - runStartMs;
        state = IDLE;
        handler.removeCallbacks(tickRunnable);
        errorListener.onError(msg);
    }

    // ---- read-only snapshot for UI ----

    int state() { return state; }
    int requestedTps() { return requestedTps; }
    long sent() { return sent; }
    long completed() { return completed; }

    long elapsedMs() {
        long a = activeMs;
        if (state == RUNNING) a += SystemClock.uptimeMillis() - runStartMs;
        return a;
    }

    String report() {
        long el = elapsedMs();
        double act = TapMath.actualTps(completed, el);
        return "Requested : " + requestedTps + " taps/s"
                + "\nActual    : " + String.format(Locale.US, "%.1f", act) + " taps/s (confirmed by Android)"
                + "\nConfirmed : " + completed
                + "\nSent      : " + sent
                + "\nLate slots: " + late
                + "\nCancelled : " + cancelled
                + "\nRejected  : " + rejected
                + "\nTime      : " + (el / 1000) + " s";
    }
}
