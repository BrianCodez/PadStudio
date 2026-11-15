package com.briancodez.padstudio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class PadView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<String, RectF> targets = new LinkedHashMap<>();
    public String selected = "A";
    public Consumer<String> onSelect;
    private final int accent = 0xFF66C0F4, ink = 0xFFEEF5FC;

    public PadView(Context context) {
        super(context); setFocusable(true);
        setContentDescription("Controller diagram. Select inputs using the input buttons below.");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, Math.round(width * .66f));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = getWidth() / 440f;
        canvas.save(); canvas.scale(scale, scale);
        paint.setColor(0xFF304355); paint.setStyle(Paint.Style.FILL);
        Path body = new Path();
        body.moveTo(114, 78); body.cubicTo(53, 78, 43, 127, 29, 203);
        body.cubicTo(16, 264, 55, 278, 87, 229); body.lineTo(120, 193);
        body.lineTo(320, 193); body.lineTo(353, 229);
        body.cubicTo(385, 278, 424, 264, 411, 203);
        body.cubicTo(397, 127, 387, 78, 326, 78); body.close();
        canvas.drawPath(body, paint);
        targets.clear();
        rectangle(canvas, "LB", 66, 56, 145, 77, 9);
        rectangle(canvas, "RB", 295, 56, 374, 77, 9);
        rectangle(canvas, "LT", 70, 13, 138, 49, 8);
        rectangle(canvas, "RT", 302, 13, 370, 49, 8);
        circle(canvas, "L3", 110, 133, 33, "L");
        circle(canvas, "R3", 269, 221, 33, "R");
        circle(canvas, "Y", 344, 114, 22, "Y");
        circle(canvas, "X", 305, 153, 22, "X");
        circle(canvas, "B", 383, 153, 22, "B");
        circle(canvas, "A", 344, 192, 22, "A");
        rectangle(canvas, "View", 178, 113, 215, 145, 9);
        rectangle(canvas, "Menu", 225, 113, 262, 145, 9);
        rectangle(canvas, "Up", 166, 172, 194, 200, 4);
        rectangle(canvas, "Down", 166, 230, 194, 258, 4);
        rectangle(canvas, "Left", 137, 201, 165, 229, 4);
        rectangle(canvas, "Right", 195, 201, 223, 229, 4);
        canvas.restore();
    }
    private void rectangle(Canvas canvas, String id, float left, float top, float right, float bottom, float radius) {
        RectF bounds = new RectF(left, top, right, bottom); targets.put(id, bounds);
        paint.setColor(selected.equals(id) ? accent : 0xFF101823); canvas.drawRoundRect(bounds, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1); paint.setColor(selected.equals(id) ? accent : 0xFF496078);
        canvas.drawRoundRect(bounds, radius, radius, paint); paint.setStyle(Paint.Style.FILL);
        label(canvas, id, bounds.centerX(), bounds.centerY(), id.length() > 3 ? 11 : 14, selected.equals(id));
    }
    private void circle(Canvas canvas, String id, float x, float y, float radius, String text) {
        targets.put(id, new RectF(x - radius, y - radius, x + radius, y + radius));
        if (selected.equals(id)) { paint.setColor(0x3366C0F4); canvas.drawCircle(x, y, radius + 5, paint); }
        paint.setColor(selected.equals(id) ? accent : 0xFF101823); canvas.drawCircle(x, y, radius, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1); paint.setColor(0xFF496078);
        canvas.drawCircle(x, y, radius, paint); paint.setStyle(Paint.Style.FILL);
        label(canvas, text, x, y, 17, selected.equals(id));
    }
    private void label(Canvas canvas, String text, float x, float y, float size, boolean active) {
        paint.setTextSize(size); paint.setTextAlign(Paint.Align.CENTER); paint.setColor(active ? 0xFF081B2A : ink);
        canvas.drawText(text, x, y - (paint.ascent() + paint.descent()) / 2, paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float scale = getWidth() / 440f;
            for (Map.Entry<String, RectF> entry : targets.entrySet()) {
                if (entry.getValue().contains(event.getX() / scale, event.getY() / scale)) {
                    selected = entry.getKey(); invalidate(); performClick();
                    if (onSelect != null) onSelect.accept(selected); return true;
                }
            }
        }
        return super.onTouchEvent(event);
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
