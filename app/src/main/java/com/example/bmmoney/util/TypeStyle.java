package com.example.bmmoney.util;

import com.example.bmmoney.R;

/**
 * Ban va 03/08. Mot cho duy nhat quyet dinh mau va nen cua sau loai ghi chu,
 * de danh sach, popup chi tiet, bao cao cong no va man Lich luon dong bo nhau.
 *
 * <p>Quy uoc mau: tien ra vi dung tong am (burnt / sandy), tien vao vi dung
 * tong duong (olive / dark green).</p>
 */
public final class TypeStyle {

    private TypeStyle() {
    }

    /** Nen o tron ben trai moi dong. */
    public static int bg(String type) {
        String t = Stats.normalize(type);
        if (Stats.INCOME.equals(t)) return R.drawable.bg_income;
        if (Stats.BORROW.equals(t)) return R.drawable.bg_borrow;
        if (Stats.REPAY.equals(t)) return R.drawable.bg_repay;
        if (Stats.LEND.equals(t)) return R.drawable.bg_lend;
        if (Stats.COLLECT.equals(t)) return R.drawable.bg_collect;
        return R.drawable.bg_expense;
    }

    /** Mau chu cua so tien. */
    public static int color(String type) {
        String t = Stats.normalize(type);
        if (Stats.INCOME.equals(t)) return R.color.line_income;
        if (Stats.BORROW.equals(t)) return R.color.line_borrow;
        if (Stats.REPAY.equals(t)) return R.color.line_repay;
        if (Stats.LEND.equals(t)) return R.color.line_lend;
        if (Stats.COLLECT.equals(t)) return R.color.line_collect;
        return R.color.line_expense;
    }
}
