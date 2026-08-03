package com.example.bmmoney.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Bieu do "Ghi chu tai chinh theo chu ky".
 *
 * <p><b>Ban va 03/08 - ve lai cho day du:</b> ban cu chi co bon duong tran tren mot
 * luoi o vuong, khong he co truc gia tri. Nhin vao chi doan duoc duong nao cao hon
 * duong nao, khong biet cao BAO NHIEU, cung khong biet ky nao la ky moi nhat.</p>
 *
 * <p>Nay bo sung:</p>
 * <ol>
 *   <li>Truc doc co ba moc gia tri (0 - giua - cao nhat) viet gon: 12,5tr / 800n.</li>
 *   <li>Luoi ngang net dut nhat, bo luoi doc cho do roi.</li>
 *   <li>To nen mo duoi duong chi tieu de mat bat ngay duoc duong chinh.</li>
 *   <li>Diem cuoi moi duong to hon va co vong sang, danh dau ky hien tai.</li>
 *   <li>Nhan ky moi nhat duoc in dam.</li>
 *   <li>Chua co so lieu thi hien mot dong chu thay vi bon duong nam bet o day.</li>
 * </ol>
 *
 * <p>Bon duong van dung CHUNG mot moc cao nhat de so sanh duoc do lon that su.
 * Duong nao toan gia tri 0 thi khong ve.</p>
 */
public class TrendChartView extends View {

    private static final float VW = 320f;
    private static final float VH = 180f;

    /** Vung ve: chua cho nhan truc doc ben trai va nhan ky ben duoi. */
    private static final float TOP = 24f;
    private static final float BOTTOM = 136f;
    private static final float LEFT = 52f;
    private static final float RIGHT = 308f;
    private static final float SPAN = RIGHT - LEFT;

    public static final int COLOR_EXPENSE = Color.parseColor("#BC6C25");
    public static final int COLOR_INCOME = Color.parseColor("#606C38");
    public static final int COLOR_LEND = Color.parseColor("#DDA15E");
    public static final int COLOR_DEBT = Color.parseColor("#283618");

    private static final int[] COLORS = {COLOR_EXPENSE, COLOR_INCOME, COLOR_LEND, COLOR_DEBT};

    private static final int GRID = Color.parseColor("#E4DCBB");
    private static final int AXIS_TEXT = Color.parseColor("#A0925A");
    private static final int LABEL_TEXT = Color.parseColor("#606C38");
    private static final int CARD_BG = Color.parseColor("#FFFEF5");

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
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path fillPath = new Path();

    public TrendChartView(Context context) {
        this(context, null);
    }

    public TrendChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        gridPaint.setColor(GRID);
        gridPaint.setStyle(Paint.Style.STROKE);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        labelPaint.setColor(LABEL_TEXT);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        axisPaint.setColor(AXIS_TEXT);
        axisPaint.setTextAlign(Paint.Align.RIGHT);

        emptyPaint.setColor(AXIS_TEXT);
        emptyPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** Cach goi cu: chi mot duong chi tieu. Giu lai de khong lam vo code cu. */
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
        final int steps = series[0].length;

        // Chung mot moc cao nhat cho ca bon duong de so sanh duoc voi nhau
        double rawMax = 0;
        for (double[] line : series) {
            if (line == null) continue;
            for (double v : line) rawMax = Math.max(rawMax, v);
        }

        // Chua ghi gi thi noi thang ra, dung ve bon duong nam bet o day
        if (rawMax <= 0) {
            drawGrid(canvas, sx, sy, unit, 0);
            drawStepLabels(canvas, sx, sy, unit, steps);
            emptyPaint.setTextSize(11f * unit);
            canvas.drawText("Ch\u01b0a c\u00f3 s\u1ed1 li\u1ec7u trong c\u00e1c k\u1ef3 n\u00e0y",
                    (LEFT + RIGHT) / 2f * sx, 84f * sy, emptyPaint);
            return;
        }

        // Lam tron moc tren len so dep de nhan truc doc de doc
        final double max = niceCeil(rawMax);

        drawGrid(canvas, sx, sy, unit, max);

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

            final int count = Math.min(line.length, steps);

            // Rieng duong chi tieu duoc to nen mo, day la duong nguoi dung nhin nhieu nhat
            if (s == 0 && count > 1) {
                fillPath.reset();
                fillPath.moveTo(px(0, steps) * sx, BOTTOM * sy);
                for (int i = 0; i < count; i++) {
                    fillPath.lineTo(px(i, steps) * sx, py(line[i], max) * sy);
                }
                fillPath.lineTo(px(count - 1, steps) * sx, BOTTOM * sy);
                fillPath.close();
                fillPaint.setColor((COLORS[0] & 0x00FFFFFF) | 0x1F000000);
                canvas.drawPath(fillPath, fillPaint);
            }

            linePaint.setColor(COLORS[s]);
            linePaint.setStrokeWidth(2.4f * unit);
            dotPaint.setColor(COLORS[s]);
            path.reset();

            for (int i = 0; i < count; i++) {
                float x = px(i, steps) * sx;
                float y = py(line[i], max) * sy;
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            canvas.drawPath(path, linePaint);

            for (int i = 0; i < count; i++) {
                float x = px(i, steps) * sx;
                float y = py(line[i], max) * sy;
                boolean last = i == count - 1;
                if (last) {
                    // Ky moi nhat: cham to hon, co vien nen mau kem de noi han len
                    dotPaint.setColor(CARD_BG);
                    canvas.drawCircle(x, y, 5.4f * unit, dotPaint);
                    dotPaint.setColor(COLORS[s]);
                    canvas.drawCircle(x, y, 4.2f * unit, dotPaint);
                } else {
                    canvas.drawCircle(x, y, 2.8f * unit, dotPaint);
                }
            }
        }

        drawStepLabels(canvas, sx, sy, unit, steps);
    }

    /** Luoi ngang net dut + ba moc gia tri ben trai. */
    private void drawGrid(Canvas canvas, float sx, float sy, float unit, double max) {
        gridPaint.setStrokeWidth(1f);
        gridPaint.setPathEffect(new DashPathEffect(
                new float[]{3f * unit, 3f * unit}, 0f));
        axisPaint.setTextSize(9f * unit);

        for (int i = 0; i <= 2; i++) {
            float ratio = i / 2f;                       // 0 = day, 1 = dinh
            float y = (BOTTOM - (BOTTOM - TOP) * ratio) * sy;
            canvas.drawLine(LEFT * sx, y, RIGHT * sx, y, gridPaint);
            if (max > 0) {
                canvas.drawText(compact(max * ratio), (LEFT - 8f) * sx, y + 3f * unit, axisPaint);
            }
        }
        gridPaint.setPathEffect(null);
    }

    /** Nhan cac ky duoi truc ngang, ky moi nhat in dam. */
    private void drawStepLabels(Canvas canvas, float sx, float sy, float unit, int steps) {
        labelPaint.setTextSize(10f * unit);
        int count = Math.min(labels.length, steps);
        for (int i = 0; i < count; i++) {
            boolean last = i == count - 1;
            labelPaint.setFakeBoldText(last);
            labelPaint.setColor(last ? COLOR_DEBT : LABEL_TEXT);
            canvas.drawText(labels[i], px(i, steps) * sx, (BOTTOM + 18f) * sy, labelPaint);
        }
        labelPaint.setFakeBoldText(false);
    }

    /** 12.500.000 -> "12,5tr"; 800.000 -> "800n"; 0 -> "0". */
    private static String compact(double value) {
        double abs = Math.abs(value);
        if (abs < 1) return "0";
        if (abs >= 1_000_000_000d) return trim(value / 1_000_000_000d) + "t\u1ef7";
        if (abs >= 1_000_000d) return trim(value / 1_000_000d) + "tr";
        if (abs >= 1_000d) return trim(value / 1_000d) + "n";
        return String.valueOf(Math.round(value));
    }

    private static String trim(double value) {
        if (Math.abs(value - Math.round(value)) < 0.05d) return String.valueOf(Math.round(value));
        return String.format(java.util.Locale.getDefault(), "%.1f", value).replace('.', ',');
    }

    /** Lam tron moc tren len 1 / 2 / 5 nhan luy thua 10 cho nhan truc doc de nhin. */
    private static double niceCeil(double value) {
        if (value <= 0) return 1;
        double exp = Math.pow(10, Math.floor(Math.log10(value)));
        double base = value / exp;
        double nice;
        if (base <= 1) nice = 1;
        else if (base <= 2) nice = 2;
        else if (base <= 5) nice = 5;
        else nice = 10;
        return nice * exp;
    }

    private float px(int index, int steps) {
        return LEFT + index * (SPAN / Math.max(1, steps - 1));
    }

    private float py(double value, double max) {
        return (float) (BOTTOM - (value / max) * (BOTTOM - TOP));
    }
}
