package com.example.bmmoney.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Bieu do duong xu huong, ve lai dung SVG 320x180 cua thiet ke:
 * luoi 40x30, duong gradient olive -> burnt, 7 diem, nhan thang duoi.
 * Co the nap du lieu that qua setData(...).
 */
public class TrendChartView extends View {

    private static final float VW = 320f;
    private static final float VH = 180f;

    private float[][] points = {
            {40, 120}, {80, 80}, {120, 100}, {160, 60}, {200, 90}, {240, 40}, {280, 70}
    };
    private String[] months = {"T8", "T9", "T10", "T11", "T12", "T1", "T2"};
    private int highlightIndex = 5;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public TrendChartView(Context context) {
        this(context, null);
    }

    public TrendChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        gridPaint.setColor(Color.parseColor("#F0E8C8"));
        gridPaint.setStrokeWidth(1f);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        labelPaint.setColor(Color.parseColor("#606C38"));
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** Nap chi tieu that theo thang: values[i] tuong ung labels[i]. */
    public void setData(double[] values, String[] labels) {
        if (values == null || values.length == 0) return;
        double max = 0;
        for (double v : values) max = Math.max(max, v);
        if (max <= 0) max = 1;

        float[][] pts = new float[values.length][2];
        for (int i = 0; i < values.length; i++) {
            pts[i][0] = 40f + i * (240f / Math.max(1, values.length - 1));
            pts[i][1] = (float) (140f - (values[i] / max) * 100f);
        }
        this.points = pts;
        if (labels != null && labels.length == values.length) {
            this.months = labels;
        }
        this.highlightIndex = values.length - 1;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float sx = getWidth() / VW;
        float sy = getHeight() / VH;

        for (float x = 0; x <= VW; x += 40f) {
            canvas.drawLine(x * sx, 0, x * sx, getHeight(), gridPaint);
        }
        for (float y = 0; y <= VH; y += 30f) {
            canvas.drawLine(0, y * sy, getWidth(), y * sy, gridPaint);
        }

        linePaint.setStrokeWidth(3f * Math.min(sx, sy));
        linePaint.setShader(new LinearGradient(0, 0, getWidth(), 0,
                Color.parseColor("#606C38"), Color.parseColor("#BC6C25"), Shader.TileMode.CLAMP));

        path.reset();
        for (int i = 0; i < points.length; i++) {
            float px = points[i][0] * sx;
            float py = points[i][1] * sy;
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        canvas.drawPath(path, linePaint);

        for (int i = 0; i < points.length; i++) {
            dotPaint.setColor(Color.parseColor(i == highlightIndex ? "#BC6C25" : "#606C38"));
            canvas.drawCircle(points[i][0] * sx, points[i][1] * sy, 4.5f * Math.min(sx, sy), dotPaint);
        }

        labelPaint.setTextSize(10f * Math.min(sx, sy) * 1.2f);
        for (int i = 0; i < months.length && i < points.length; i++) {
            canvas.drawText(months[i], points[i][0] * sx, 170f * sy, labelPaint);
        }
    }
}
