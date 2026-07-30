package com.example.bmmoney.util;

import android.content.Context;

import java.util.Calendar;
import java.util.Locale;

/**
 * Chu ky thu chi tuy chinh: nguoi dung chon ngay chot (dd/mm) trong Cai dat.
 * Vi du ngay chot = 2, hom nay 27/7/2026 -> ky hien tai la 2/7/2026 - 2/8/2026,
 * con 6 ngay, nhan hien thi "7/2026 - 8/2026".
 */
public final class Cycle {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private Cycle() {
    }

    private static Calendar midnight(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    private static void setDayClamped(Calendar c, int day) {
        c.set(Calendar.DAY_OF_MONTH, Math.min(day, c.getActualMaximum(Calendar.DAY_OF_MONTH)));
    }

    /** Moc bat dau cua ky dang chua thoi diem "now". */
    public static long start(int cycleDay, long now) {
        Calendar c = midnight(now);
        setDayClamped(c, cycleDay);
        if (c.getTimeInMillis() > midnight(now).getTimeInMillis()) {
            c.add(Calendar.MONTH, -1);
            setDayClamped(c, cycleDay);
        }
        return c.getTimeInMillis();
    }

    /** Moc ket thuc (ngay chot ke tiep) cua ky dang chua "now". */
    public static long end(int cycleDay, long now) {
        Calendar c = midnight(start(cycleDay, now));
        c.add(Calendar.MONTH, 1);
        setDayClamped(c, cycleDay);
        return c.getTimeInMillis();
    }

    /**
     * Moc dau/cuoi cua ky lech "offset" so voi ky hien tai.
     * offset = 0 -> ky nay, -1 -> ky truoc, -6 -> 6 ky truoc.
     */
    public static long[] bounds(int cycleDay, long now, int offset) {
        Calendar c = midnight(start(cycleDay, now));
        c.add(Calendar.MONTH, offset);
        setDayClamped(c, cycleDay);
        long from = c.getTimeInMillis();
        c.add(Calendar.MONTH, 1);
        setDayClamped(c, cycleDay);
        long to = c.getTimeInMillis() - 1;
        return new long[]{from, to};
    }

    /** So ngay con lai tinh den ngay chot ke tiep. */
    public static int daysLeft(int cycleDay, long now) {
        long diff = midnight(end(cycleDay, now)).getTimeInMillis() - midnight(now).getTimeInMillis();
        int days = (int) (diff / DAY_MS);
        return Math.max(0, days);
    }

    /**
     * Nhan hai moc thoi gian, vi du "7/2026 - 8/2026".
     *
     * <p>"to" tra ve tu {@link #bounds(int, long, int)} la thoi diem ngay truoc lan chot ke tiep
     * (23:59:59.999 hom truoc). Neu lay thang cua moc do thi ngay chot = 1 se cho ra
     * "7/2026 - 7/2026". Vi vay o day ta cong them 1 mili giay de lay dung ngay chot ke tiep.</p>
     */
    public static String rangeLabel(long from, long to) {
        Calendar a = midnight(from);
        Calendar b = midnight(to + 1);
        return String.format(Locale.US, "%d/%d - %d/%d",
                a.get(Calendar.MONTH) + 1, a.get(Calendar.YEAR),
                b.get(Calendar.MONTH) + 1, b.get(Calendar.YEAR));
    }

    /**
     * Chi so ky trong nam (L1, L2, L3...):
     * (thoi diem hien tai - dau nam) / do dai mot ky thu chi.
     */
    public static int indexInYear(int cycleDay, long time) {
        Calendar c = midnight(start(cycleDay, time));
        return c.get(Calendar.MONTH) + 1;
    }

    /** Nhan ngan cua mot ky, vi du "L7". */
    public static String label(int cycleDay, long time) {
        return "L" + indexInYear(cycleDay, time);
    }

    /** Ngay dau nam hien tai. */
    public static long startOfYear(long now) {
        Calendar c = midnight(now);
        c.set(Calendar.MONTH, Calendar.JANUARY);
        c.set(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    /** Ngay cuoi nam hien tai. */
    public static long endOfYear(long now) {
        Calendar c = midnight(startOfYear(now));
        c.add(Calendar.YEAR, 1);
        return c.getTimeInMillis() - 1;
    }

    /** Dau tuan (thu Hai) cua thoi diem now. */
    public static long startOfWeek(long now) {
        Calendar c = midnight(now);
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        if (c.getTimeInMillis() > midnight(now).getTimeInMillis()) {
            c.add(Calendar.WEEK_OF_YEAR, -1);
        }
        return c.getTimeInMillis();
    }

    /** Nhan dd/mm cua ngay chot dang luu. */
    public static String cycleDayLabel(Context context) {
        return String.format(Locale.US, "%02d/%02d",
                Prefs.cycleDay(context), Prefs.cycleMonth(context));
    }
}
