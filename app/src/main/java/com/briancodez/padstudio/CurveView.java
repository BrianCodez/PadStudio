package com.briancodez.padstudio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

public final class CurveView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public Model.Stick stick;
    public boolean tester;
    public CurveView(Context context) { super(context); setContentDescription("Stick response preview"); }
    @Override protected void onMeasure(int width, int height) { setMeasuredDimension(MeasureSpec.getSize(width), Math.round(MeasureSpec.getSize(width) * .58f)); }
    @Override protected void onDraw(Canvas canvas) {
        float width = getWidth(), height = getHeight();
        if (tester) {
            float radius = Math.min(width / 5, height / 2.5f);
            drawStick(canvas, width * .27f, height * .44f, radius, RuntimeState.leftX, RuntimeState.leftY, "Left stick");
            drawStick(canvas, width * .73f, height * .44f, radius, RuntimeState.rightX, RuntimeState.rightY, "Right stick");
            return;
        }
        if (stick == null) return;
        float left = 38, right = width - 20, top = 20, bottom = height - 40;
        paint.setColor(0xFF33465B); paint.setStrokeWidth(2);
        canvas.drawLine(left, top, left, bottom, paint); canvas.drawLine(left, bottom, right, bottom, paint);
        paint.setColor(0xFF496078); canvas.drawLine(left, bottom, right, top, paint);
        Path path = new Path();
        for (int i = 0; i <= 100; i++) {
            float x = left + (right - left) * i / 100f;
            float y = bottom - (bottom - top) * InputMath.response(i / 100f, stick.deadzone, stick.sensitivity, stick.curve);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        paint.setColor(0xFF66C0F4); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(4); canvas.drawPath(path, paint); paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFA9BBCE); paint.setTextSize(26); paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Stick travel →", width / 2, height - 5, paint);
    }
    private void drawStick(Canvas canvas, float x, float y, float radius, float axisX, float axisY, String label) {
        paint.setColor(0xFF243346); canvas.drawCircle(x, y, radius, paint);
        paint.setColor(0xFF496078); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); canvas.drawCircle(x, y, radius, paint); paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF66C0F4); canvas.drawCircle(x + axisX * radius, y + axisY * radius, radius * .16f, paint);
        paint.setColor(0xFFA9BBCE); paint.setTextSize(28); paint.setTextAlign(Paint.Align.CENTER); canvas.drawText(label, x, y + radius + 40, paint);
    }
}
