package com.swifttap;

import android.content.Context;
import android.content.SharedPreferences;

final class AppSettings {
    static final int MAX_POINTS = 5;
    private final SharedPreferences sp;

    AppSettings(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("swifttap", Context.MODE_PRIVATE);
    }

    int tps() { return TapMath.clampTps(sp.getInt("tps", 10)); }
    int holdMs() { return TapMath.clampHold(sp.getInt("hold", 5)); }
    boolean sequential() { return sp.getBoolean("seq", false); }
    int pointCount() { return Math.max(1, Math.min(MAX_POINTS, sp.getInt("pc", 1))); }
    int pointX(int i, int def) { return sp.getInt("px" + i, def); }
    int pointY(int i, int def) { return sp.getInt("py" + i, def); }
    int panelX(int def) { return sp.getInt("panelx", def); }
    int panelY(int def) { return sp.getInt("panely", def); }

    void setRate(int tps, int hold) {
        sp.edit().putInt("tps", TapMath.clampTps(tps)).putInt("hold", TapMath.clampHold(hold)).apply();
    }

    void setSequential(boolean v) { sp.edit().putBoolean("seq", v).apply(); }
    void setPointCount(int n) { sp.edit().putInt("pc", n).apply(); }
    void setPoint(int i, int x, int y) { sp.edit().putInt("px" + i, x).putInt("py" + i, y).apply(); }
    void setPanel(int x, int y) { sp.edit().putInt("panelx", x).putInt("panely", y).apply(); }
}
