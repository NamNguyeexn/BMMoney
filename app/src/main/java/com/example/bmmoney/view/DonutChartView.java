package com.example.bmmoney.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Bieu do tron chi tieu theo danh muc.
 * Vong tron duoc ve theo kich thuoc thuc cua view, chu o giua duoc thu nho
 * va can giua trong long vong tron nen khong con de len cac lat cat.
 */
public class DonutChartView extends View {

    private static final int[] COLORS = {
            Color.parseColor("#BC6C25"),
            Color.parseColor("#606C38"),
            Color.parseColor("#DDA15E"),
            Color.parseColor("#283618"),
            Color.parseColor("#A0925A")
    };

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private float[] percents = new float[0];
    private String centerValue = "";
    private String centerSub = "";

    public DonutChartView(Context context) {
        super(context);
        init();
    }

    public DonutChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.BUTT);

        valuePaint.setColor(Color.parseColor("#283618"));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        capPaint.setColor(Color.parseColor("#606C38"));
        capPaint.setTextAlign(Paint.Align.CENTER);
        capPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        subPaint.setColor(Color.parseColor("#BC6C25"));
        subPaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    /**
     * @param percents ty le cac lat cat (tong ~100)
     * @param centerValue so tien tong cua ky (dong chinh)
     * @param centerSub ghi chu ngan, vi du "\u2191 8,5% so v\u1edbi k\u1ef3 tr\u01b0\u1edbc"
     */
    public void setData(float[] percents, String centerValue, String centerSub) {
        this.percents = percents == null ? new float[0] : percents;
        this.centerValue = centerValue == null ? "" : centerValue;
        this.centerSub = centerSub == null ? "" : centerSub;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(w, h) / 2f - dp(6);
        if (radius <= 0) return;

        float ring = radius * 0.26f;
        arcPaint.setStrokeWidth(ring);
        float arcRadius = radius - ring / 2f;
        oval.set(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius);

        float total = 0f;
        for (float p : percents) total += p;
        if (total <= 0f) {
            arcPaint.setColor(Color.parseColor("#F0E8C8"));
            canvas.drawArc(oval, 0f, 360f, false, arcPaint);
        } else {
            float start = -90f;
            for (int i = 0; i < percents.length; i++) {
                float sweep = percents[i] / total * 360f;
                arcPaint.setColor(COLORS[i % COLORS.length]);
                canvas.drawArc(oval, start + 1f, Math.max(0f, sweep - 2f), false, arcPaint);
                start += sweep;
            }
        }

        float inner = radius - ring;
        float valueSize = Math.min(inner * 0.34f, dp(22));
        valuePaint.setTextSize(valueSize);
        capPaint.setTextSize(valueSize * 0.5f);
        subPaint.setTextSize(valueSize * 0.52f);

        // Rut ngan neu chu vuot qua long vong tron
        String value = centerValue;
        float maxWidth = inner * 1.7f;
        while (value.length() > 4 && valuePaint.measureText(value) > maxWidth) {
            valueSize -= dp(1);
            valuePaint.setTextSize(valueSize);
        }

        canvas.drawText("T\u1ed4NG K\u1ef2", cx, cy - valueSize * 0.95f, capPaint);
        canvas.drawText(value, cx, cy + valueSize * 0.32f, valuePaint);
        if (!centerSub.isEmpty()) {
            canvas.drawText(centerSub, cx, cy + valueSize * 1.45f, subPaint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
