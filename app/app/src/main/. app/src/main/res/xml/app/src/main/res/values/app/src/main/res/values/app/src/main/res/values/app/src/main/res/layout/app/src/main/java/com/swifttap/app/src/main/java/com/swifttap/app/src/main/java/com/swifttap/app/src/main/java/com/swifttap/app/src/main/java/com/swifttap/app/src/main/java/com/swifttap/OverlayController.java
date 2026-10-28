package com.swifttap;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Floating panel + numbered tap-point markers, drawn as accessibility overlays (no extra permission needed). */
final class OverlayController {
    private static final int BASE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

    private final TapService service;
    private final TapScheduler scheduler;
    private final AppSettings settings;
    private final WindowManager wm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final float density;
    private final int markerSize;

    private final TapView[] markers = new TapView[AppSettings.MAX_POINTS];
    private final WindowManager.LayoutParams[] markerLp = new WindowManager.LayoutParams[AppSettings.MAX_POINTS];

    private LinearLayout panel;
    private WindowManager.LayoutParams panelLp;
    private TextView statsView, playBtn, seqBtn;
    private boolean shown;
    private boolean markersLive;
    private int pointCount;
    private final StringBuilder sb = new StringBuilder(64);

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (!shown) return;
            int st = scheduler.state();
            boolean live = st == TapScheduler.RUNNING;
            if (live != markersLive) setMarkersLive(live);
            playBtn.setText(live ? "II" : "\u25B6");
            seqBtn.setTextColor(settings.sequential() ? 0xFF00E5FF : 0xFFFFFFFF);
            sb.setLength(0);
            if (st == TapScheduler.IDLE && scheduler.sent() == 0) {
                sb.append("Ready ").append(settings.tps()).append("/s");
            } else {
                long t10 = Math.round(TapMath.actualTps(scheduler.completed(), scheduler.elapsedMs()) * 10);
                sb.append(t10 / 10).append('.').append(t10 % 10).append('/')
                        .append(scheduler.requestedTps()).append(" tps");
                if (st == TapScheduler.PAUSED) sb.append(" (paused)");
                sb.append('\n').append(scheduler.completed()).append(" taps  ");
                long s = scheduler.elapsedMs() / 1000;
                sb.append(s / 60).append(':');
                if (s % 60 < 10) sb.append('0');
                sb.append(s % 60);
            }
            statsView.setText(sb);
            ui.postDelayed(this, 250);
        }
    };

    OverlayController(TapService s, TapScheduler sch, AppSettings st) {
        service = s;
        scheduler = sch;
        settings = st;
        wm = (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
        density = s.getResources().getDisplayMetrics().density;
        markerSize = TapView.sizePx(s);
    }

    boolean isShown() { return shown; }

    private int dp(int v) { return Math.round(v * density); }

    private WindowManager.LayoutParams newParams(int w, int h, int flags) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        return lp;
    }

    void show() {
        if (shown) return;
        try {
            pointCount = settings.pointCount();
            for (int i = 0; i < pointCount; i++) addMarker(i);
            panelLp = newParams(WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT, BASE_FLAGS);
            panelLp.x = settings.panelX(dp(8));
            panelLp.y = settings.panelY(dp(80));
            buildPanel();
            wm.addView(panel, panelLp);
            shown = true;
            markersLive = false;
            ui.post(refresh);
        } catch (RuntimeException e) {
            service.reportError("Could not show overlay: " + e.getMessage());
            removeAll();
        }
    }

    void hide() {
        ui.removeCallbacks(refresh);
        scheduler.stop();
        removeAll();
    }

    void destroy() {
        hide();
    }

    private void removeAll() {
        for (int i = 0; i < markers.length; i++) removeMarker(i);
        if (panel != null) {
            try {
                wm.removeView(panel);
            } catch (IllegalArgumentException ignored) {
            }
            panel = null;
        }
        shown = false;
        markersLive = false;
    }

    private void addMarker(int i) {
        TapView v = new TapView(service, i + 1);
        WindowManager.LayoutParams lp = newParams(markerSize, markerSize, BASE_FLAGS);
        DisplayMetrics dm = service.getResources().getDisplayMetrics();
        lp.x = settings.pointX(i, dm.widthPixels / 2 - markerSize / 2 + (i - 2) * (markerSize + dp(8)));
        lp.y = settings.pointY(i, dm.heightPixels / 3);
        v.setOnTouchListener(new DragListener(v, lp, i));
        wm.addView(v, lp);
        markers[i] = v;
        markerLp[i] = lp;
    }

    private void removeMarker(int i) {
        if (markers[i] == null) return;
        try {
            wm.removeView(markers[i]);
        } catch (IllegalArgumentException ignored) {
        }
        markers[i] = null;
        markerLp[i] = null;
    }

    private void setMarkersLive(boolean live) {
        markersLive = live;
        for (int i = 0; i < markers.length; i++) {
            if (markers[i] == null) continue;
            markerLp[i].flags = BASE_FLAGS | (live ? WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE : 0);
            markers[i].setAlpha(live ? 0.3f : 1f);
            try {
                wm.updateViewLayout(markers[i], markerLp[i]);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void buildPanel() {
        panel = new LinearLayout(service);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(6), dp(6), dp(10), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0121218);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0xFF2E2E44);
        panel.setBackground(bg);

        TextView handle = new TextView(service);
        handle.setText("\u2261");
        handle.setTextColor(0xFF8A8AA0);
        handle.setTextSize(24);
        handle.setPadding(dp(8), dp(4), dp(8), dp(4));
        handle.setOnTouchListener(new DragListener(panel, panelLp, -1));
        panel.addView(handle);

        statsView = new TextView(service);
        statsView.setTextColor(0xFFE0E0F0);
        statsView.setTextSize(12);
        statsView.setMinWidth(dp(104));
        panel.addView(statsView);

        playBtn = button("\u25B6", 0xFF00C853, 16);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPlay();
            }
        });
        panel.addView(playBtn);

        TextView stopBtn = button("\u25A0", 0xFFFF5252, 16);
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scheduler.stop();
                kick();
            }
        });
        panel.addView(stopBtn);

        TextView addBtn = button("+", 0xFF3A3A55, 18);
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addPoint();
            }
        });
        panel.addView(addBtn);

        TextView subBtn = button("\u2212", 0xFF3A3A55, 18);
        subBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removePoint();
            }
        });
        panel.addView(subBtn);

        seqBtn = button("SEQ", 0xFF3A3A55, 11);
        seqBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settings.setSequential(!settings.sequential());
                kick();
            }
        });
        panel.addView(seqBtn);
    }

    private TextView button(String t, int color, float sp) {
        TextView b = new TextView(service);
        b.setText(t);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(sp);
        b.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        b.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void kick() {
        ui.removeCallbacks(refresh);
        ui.postDelayed(refresh, 40);
    }

    private void onPlay() {
        int st = scheduler.state();
        if (st == TapScheduler.RUNNING) {
            scheduler.pause();
        } else if (st == TapScheduler.PAUSED) {
            setMarkersLive(true);
            scheduler.resume();
        } else {
            startTapping();
        }
        kick();
    }

    private void startTapping() {
        int n = pointCount;
        float[] xs = new float[n];
        float[] ys = new float[n];
        int[] loc = new int[2];
        for (int i = 0; i < n; i++) {
            markers[i].getLocationOnScreen(loc);
            xs[i] = loc[0] + markers[i].getWidth() / 2f;
            ys[i] = loc[1] + markers[i].getHeight() / 2f;
        }
        setMarkersLive(true);
        scheduler.start(xs, ys, settings.tps(), settings.holdMs(), settings.sequential());
    }

    private void addPoint() {
        if (scheduler.state() == TapScheduler.RUNNING || pointCount >= AppSettings.MAX_POINTS) return;
        try {
            addMarker(pointCount);
            pointCount++;
            settings.setPointCount(pointCount);
        } catch (RuntimeException e) {
            service.reportError("Could not add point: " + e.getMessage());
        }
    }

    private void removePoint() {
        if (scheduler.state() == TapScheduler.RUNNING || pointCount <= 1) return;
        pointCount--;
        removeMarker(pointCount);
        settings.setPointCount(pointCount);
    }

    void onConfigChanged() {
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!shown) return;
                for (int i = 0; i < markers.length; i++) {
                    if (markers[i] == null) continue;
                    clamp(markers[i], markerLp[i]);
                    try {
                        wm.updateViewLayout(markers[i], markerLp[i]);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (panel != null) {
                    clamp(panel, panelLp);
                    try {
                        wm.updateViewLayout(panel, panelLp);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }, 250);
    }

    private void clamp(View v, WindowManager.LayoutParams lp) {
        DisplayMetrics dm = service.getResources().getDisplayMetrics();
        lp.x = Math.max(0, Math.min(lp.x, dm.widthPixels - v.getWidth()));
        lp.y = Math.max(0, Math.min(lp.y, dm.heightPixels - v.getHeight()));
    }

    private final class DragListener implements View.OnTouchListener {
        private final View target;
        private final WindowManager.LayoutParams lp;
        private final int index; // -1 = panel
        private float ox, oy;

        DragListener(View target, WindowManager.LayoutParams lp, int index) {
            this.target = target;
            this.lp = lp;
            this.index = index;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    ox = lp.x - e.getRawX();
                    oy = lp.y - e.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    lp.x = (int) (e.getRawX() + ox);
                    lp.y = (int) (e.getRawY() + oy);
                    clamp(target, lp);
                    try {
                        wm.updateViewLayout(target, lp);
                    } catch (IllegalArgumentException ignored) {
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (index >= 0) settings.setPoint(index, lp.x, lp.y);
                    else settings.setPanel(lp.x, lp.y);
                    return true;
                default:
                    return false;
            }
        }
    }
}
