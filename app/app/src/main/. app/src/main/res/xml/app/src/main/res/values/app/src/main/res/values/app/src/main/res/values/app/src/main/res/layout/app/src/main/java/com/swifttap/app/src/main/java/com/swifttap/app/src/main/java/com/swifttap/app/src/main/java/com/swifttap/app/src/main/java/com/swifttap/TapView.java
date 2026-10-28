package com.swifttap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

final class TapView extends View {
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String label = "1";

    TapView(Context c, int number) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(3 * d);
        ring.setColor(0xFF00E5FF);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0x3300E5FF);
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(18 * d);
        text.setFakeBoldText(true);
        setNumber(number);
    }

    static int sizePx(Context c) {
        return Math.round(56 * c.getResources().getDisplayMetrics().density);
    }

    void setNumber(int n) {
        label = String.valueOf(n);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - ring.getStrokeWidth();
        canvas.drawCircle(cx, cy, r, fill);
        canvas.drawCircle(cx, cy, r, ring);
        canvas.drawText(label, cx, cy - (text.ascent() + text.descent()) / 2f, text);
    }
}
