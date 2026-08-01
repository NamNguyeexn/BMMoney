package com.example.bmmoney.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Bieu do xu huong ghi chu tai chinh theo chu ky.
 *
 * <p><b>Ban va 02/08:</b> tu mot duong chi tieu duy nhat nay ve BON duong trong
 * cung mot khung luoi:</p>
 * <ol>
 *   <li>Chi tieu - burnt  #BC6C25</li>
 *   <li>Thu nhap - olive  #606C38</li>
 *   <li>Cho vay  - sandy  #DDA15E</li>
 *   <li>Tra no   - dark green #283618</li>
 * </ol>
 *
 * <p>Bon duong dung CHUNG mot moc cao nhat de nhin la so sanh duoc do lon that
 * su giua cac loai. Duong nao toan gia tri 0 thi khong ve de do roi mat.
 * Chu thich mau nam o layout ngay duoi bieu do.</p>
 */
public class TrendChartView extends View {

    private static final float VW = 320f;
    private static final float VH = 180f;

    /** Vung ve: tu y = 40 (dinh) den y = 140 (day). */
    private static final float TOP = 40f;
    private static final float BOTTOM = 140f;
    private static final float LEFT = 40f;
    private static final float SPAN = 240f;

    public static final int COLOR_EXPENSE = Color.parseColor("#BC6C25");
    public static final int COLOR_INCOME = Color.parseColor("#606C38");
    public static final int COLOR_LEND = Color.parseColor("#DDA15E");
    public static final int COLOR_DEBT = Color.parseColor("#283618");

    private static final int[] COLORS = {COLOR_EXPENSE, COLOR_INCOME, COLOR_LEND, COLOR_DEBT};

    /** series[loai][ky]. Mac dinh la du lieu mau cua ban thiet ke. */
    private double[][] series = {
            {3, 5, 4, 7, 5, 8},
            {0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0}
    };
    private String[] labels = {"L1", "L2", "L3", "L4", "L5", "L6"};

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

    /**
     * Cach goi cu: chi mot duong chi tieu. Giu lai de khong lam vo code cu.
     */
    public void setData(double[] values, String[] axisLabels) {
        if (values == null || values.length == 0) return;
        double[][] one = new double[4][values.length];
        one[0] = values;
        one[1] = new double[values.length];
        one[2] = new double[values.length];
        one[3] = new double[values.length];
        setSeries(one, axisLabels);
    }

    /**
     * Nap bon duong cung luc.
     *
     * @param values     mang 4 dong theo thu tu chi / thu / cho vay / tra no,
     *                   moi dong co so cot bang nhau va bang do dai axisLabels
     * @param axisLabels nhan truc ngang, vi du L3, L4, L5
     */
    public void setSeries(double[][] values, String[] axisLabels) {
        if (values == null || values.length == 0 || values[0] == null || values[0].length == 0) return;
        this.series = values;
        if (axisLabels != null && axisLabels.length == values[0].length) {
            this.labels = axisLabels;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float sx = getWidth() / VW;
        final float sy = getHeight() / VH;
        final float unit = Math.min(sx, sy);

        for (float x = 0; x <= VW; x += 40f) {
            canvas.drawLine(x * sx, 0, x * sx, getHeight(), gridPaint);
        }
        for (float y = 0; y <= VH; y += 30f) {
            canvas.drawLine(0, y * sy, getWidth(), y * sy, gridPaint);
        }

        final int steps = series[0].length;

        // Chung mot moc cao nhat cho ca bon duong de so sanh duoc voi nhau
        double max = 0;
        for (double[] line : series) {
            if (line == null) continue;
            for (double v : line) max = Math.max(max, v);
        }
        if (max <= 0) max = 1;

        linePaint.setShader(null);
        linePaint.setStrokeWidth(2.6f * unit);

        for (int s = 0; s < series.length && s < COLORS.length; s++) {
            double[] line = series[s];
            if (line == null || line.length == 0) continue;

            boolean hasValue = false;
            for (double v : line) {
                if (v > 0) {
                    hasValue = true;
                    break;
                }
            }
            // Duong toan 0 thi bo qua, tranh bon duong chong len nhau o day bieu do
            if (!hasValue) continue;

            linePaint.setColor(COLORS[s]);
            dotPaint.setColor(COLORS[s]);
            path.reset();

            for (int i = 0; i < line.length && i < steps; i++) {
                float px = px(i, steps) * sx;
                float py = py(line[i], max) * sy;
                if (i == 0) {
                    path.moveTo(px, py);
                } else {
                    path.lineTo(px, py);
                }
            }
            canvas.drawPath(path, linePaint);

            for (int i = 0; i < line.length && i < steps; i++) {
                canvas.drawCircle(px(i, steps) * sx, py(line[i], max) * sy, 3.6f * unit, dotPaint);
            }
        }

        labelPaint.setTextSize(10f * unit * 1.2f);
        for (int i = 0; i < labels.length && i < steps; i++) {
            canvas.drawText(labels[i], px(i, steps) * sx, 170f * sy, labelPaint);
        }
    }

    private float px(int index, int steps) {
        return LEFT + index * (SPAN / Math.max(1, steps - 1));
    }

    private float py(double value, double max) {
        return (float) (BOTTOM - (value / max) * (BOTTOM - TOP));
    }
}
