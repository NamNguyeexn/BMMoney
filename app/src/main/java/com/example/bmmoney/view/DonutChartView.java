package com.example.bmmoney.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Bieu do tron (donut) ve lai dung SVG cua thiet ke:
 * ban kinh ngoai 62, trong 40, khe 2 do, dau bo tron, chu o giua.
 * Du lieu duoc nap tu Room qua setData(...).
 */
public class DonutChartView extends View {

    private static final String[] COLORS = {"#BC6C25", "#606C38", "#DDA15E", "#283618", "#a0925a"};
    private static final String DARK_GREEN = "#283618";
    private static final String OLIVE = "#606C38";
    private static final String CREAM = "#FEFAE0";

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private float[] percents = {40f, 24f, 18f, 12f, 6f};
    private String centerLabel = "T\u1ed5ng th\u00e1ng";
    private String centerValue = "65,5 tr \u20ab";
    private String centerSub = "\u2193 8.5% th\u00e1ng tr\u01b0\u1edbc";

    public DonutChartView(Context context) {
        this(context, null);
    }

    public DonutChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** Nap du lieu that: ty le tung danh muc va chu o tam. */
    public void setData(float[] percents, String centerValue, String centerSub) {
        if (percents != null && percents.length > 0) {
            this.percents = percents;
        }
        if (centerValue != null) this.centerValue = centerValue;
        if (centerSub != null) this.centerSub = centerSub;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // He toa do goc cua thiet ke la 160x160
        float scale = Math.min(getWidth(), getHeight()) / 160f;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float outerR = 62f * scale;
        float innerR = 40f * scale;
        arcPaint.setStrokeWidth(outerR - innerR);
        float radius = (outerR + innerR) / 2f;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);

        float cursor = 0f;
        for (int i = 0; i < percents.length; i++) {
            float start = cursor * 3.6f - 90f;
            float sweep = percents[i] * 3.6f - 2f; // khe 2 do giua cac phan
            if (sweep <= 0f) {
                cursor += percents[i];
                continue;
            }
            arcPaint.setColor(Color.parseColor(COLORS[i % COLORS.length]));
            canvas.drawArc(oval, start, sweep, false, arcPaint);
            cursor += percents[i];
        }

        textPaint.setColor(Color.parseColor(OLIVE));
        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(9f * scale * 1.15f);
        canvas.drawText(centerLabel, cx, cy - 10f * scale, textPaint);

        textPaint.setColor(Color.parseColor(DARK_GREEN));
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(12f * scale * 1.15f);
        canvas.drawText(centerValue, cx, cy + 5f * scale, textPaint);

        textPaint.setColor(Color.parseColor(OLIVE));
        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(8f * scale * 1.15f);
        canvas.drawText(centerSub, cx, cy + 19f * scale, textPaint);
    }

    public int colorAt(int index) {
        return Color.parseColor(COLORS[index % COLORS.length]);
    }

    public String creamColor() {
        return CREAM;
    }
}
