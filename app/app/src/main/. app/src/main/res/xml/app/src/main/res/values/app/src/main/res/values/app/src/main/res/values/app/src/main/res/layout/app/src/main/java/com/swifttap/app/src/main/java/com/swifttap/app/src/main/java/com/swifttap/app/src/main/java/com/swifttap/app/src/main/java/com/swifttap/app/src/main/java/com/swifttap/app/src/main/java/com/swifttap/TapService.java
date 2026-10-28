package com.swifttap;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class TapService extends AccessibilityService {
    static volatile TapService instance;
    volatile String lastError;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TapScheduler scheduler;
    private OverlayController overlay;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        lastError = null;
        scheduler = new TapScheduler(this, new TapScheduler.ErrorListener() {
            @Override
            public void onError(String message) {
                reportError(message);
            }
        });
        overlay = new OverlayController(this, scheduler, new AppSettings(this));
    }

    void reportError(final String msg) {
        lastError = msg;
        ui.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    TapScheduler scheduler() { return scheduler; }

    void showPanel() {
        if (overlay != null) overlay.show();
    }

    void hidePanel() {
        if (overlay != null) overlay.hide();
    }

    boolean isPanelShown() {
        return overlay != null && overlay.isShown();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // not used
    }

    @Override
    public void onInterrupt() {
        if (scheduler != null) scheduler.stop();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (overlay != null) overlay.onConfigChanged();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        teardown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        teardown();
        super.onDestroy();
    }

    private void teardown() {
        instance = null;
        if (overlay != null) {
            overlay.destroy();
            overlay = null;
        }
        if (scheduler != null) {
            scheduler.release();
            scheduler = null;
        }
    }
}
