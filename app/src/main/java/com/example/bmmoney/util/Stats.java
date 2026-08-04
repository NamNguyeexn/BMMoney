package com.example.bmmoney.util;

import com.example.bmmoney.data.TransactionEntity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tong hop so lieu tu danh sach giao dich Room.
 *
 * <p><b>Ban va 03/08 - SAU loai ghi chu tai chinh theo nghiep vu ke toan.</b></p>
 * <ul>
 *   <li>{@link #EXPENSE} Chi tieu - tien ra vi, tinh vao loi nhuan</li>
 *   <li>{@link #INCOME}  Thu nhap - tien vao vi, tinh vao loi nhuan</li>
 *   <li>{@link #BORROW}  Di vay   - tien VAO vi, tang no phai tra</li>
 *   <li>{@link #REPAY}   Tra no goc - tien RA vi, giam no phai tra</li>
 *   <li>{@link #LEND}    Cho vay  - tien RA vi, tang phai thu</li>
 *   <li>{@link #COLLECT} Thu hoi no goc - tien VAO vi, giam phai thu</li>
 * </ul>
 *
 * <p><b>Nguyen tac ke toan (khong tinh lai):</b></p>
 * <pre>
 * soDuVi    = INCOME - EXPENSE + BORROW - REPAY - LEND + COLLECT
 * phaiThu   = LEND   - COLLECT
 * phaiTra   = BORROW - REPAY
 * taiSanRong= soDuVi + phaiThu - phaiTra
 * loiNhuan  = INCOME - EXPENSE
 * </pre>
 *
 * <p>Bon loai cong no LAM DOI SO DU VI nhung KHONG tinh vao thu nhap hay chi tieu,
 * vi vay moi truy van ngan sach / bieu do danh muc van chi loc EXPENSE va INCOME.</p>
 */
public final class Stats {

    public static final String EXPENSE = "EXPENSE";
    public static final String INCOME = "INCOME";
    /** Di vay: minh vay nguoi khac -> tien vao vi, phat sinh no phai tra. */
    public static final String BORROW = "BORROW";
    /** Tra no goc: minh tra bot no -> tien ra vi, giam no phai tra. */
    public static final String REPAY = "REPAY";
    /** Cho vay: minh cho nguoi khac vay -> tien ra vi, phat sinh phai thu. */
    public static final String LEND = "LEND";
    /** Thu hoi no goc: nguoi ta tra minh -> tien vao vi, giam phai thu. */
    public static final String COLLECT = "COLLECT";

    /**
     * Ten loai cu (truoc ban 03/08) cua khoan "No phai tra".
     * Chi con dung khi doc du lieu cu tu cloud; trong may da doi sang BORROW
     * boi migration 2 -> 3.
     */
    public static final String LEGACY_DEBT = "DEBT";

    /**
     * Ban va 04/08 - DANH MUC RIENG CHO KHOAN THU / CHI CAN BANG.
     *
     * <p>Khoan can bang van la INCOME hoac EXPENSE de so du vi tu dong dung, nhung
     * KHONG phai chi tieu hay thu nhap thuc. Vi vay no duoc gan danh muc rieng nay va
     * moi truy van ngan sach / bieu do danh muc / lai lo deu loai danh muc nay ra
     * (xem cac ham ...Skip trong TransactionDao). Nho vay mot lan can bang 3 trieu
     * khong bop meo the "Phan tich theo danh muc".</p>
     *
     * <p>Chuoi nay duoc luu xuong SQLite va len cloud nen KHONG duoc doi. Doi la moi
     * ban ghi can bang cu se bi tinh nham thanh chi tieu that.</p>
     */
    public static final String CATEGORY_BALANCE = "C\u00e2n b\u1eb1ng s\u1ed1 d\u01b0";

    /** True neu ban ghi nay la khoan can bang, khong phai thu chi that. */
    public static boolean isBalance(String category) {
        return CATEGORY_BALANCE.equals(category);
    }

    private Stats() {
    }

    public static class Slice {
        public final String name;
        public final double total;

        public Slice(String name, double total) {
            this.name = name;
            this.total = total;
        }
    }

    // ------------------------------------------------------------- loai ghi chu

    /** Doi ten loai kieu cu sang ten moi. Dung khi doc ban sao luu cu. */
    public static String normalize(String type) {
        if (LEGACY_DEBT.equals(type)) return BORROW;
        if (type == null) return EXPENSE;
        return type;
    }

    /**
     * Dau tac dong len so du vi: +1 la tien vao vi, -1 la tien ra khoi vi.
     * Day la ham goc de tinh so du vi va de chon dau hien thi.
     */
    public static int walletSign(String type) {
        String t = normalize(type);
        if (INCOME.equals(t) || BORROW.equals(t) || COLLECT.equals(t)) return 1;
        return -1;
    }

    /** Loai nay co tinh vao lai / lo (thu nhap - chi tieu) khong. */
    public static boolean affectsProfit(String type) {
        String t = normalize(type);
        return EXPENSE.equals(t) || INCOME.equals(t);
    }

    /** Loai nay thuoc nhom cong no khong (bon loai vay - tra - cho vay - thu hoi). */
    public static boolean isDebtKind(String type) {
        return !affectsProfit(type);
    }

    /** Loai nay lam TANG khoan phai thu (minh cho vay). */
    public static boolean isReceivable(String type) {
        return LEND.equals(normalize(type));
    }

    /** Loai nay lam TANG khoan phai tra (minh di vay). */
    public static boolean isPayable(String type) {
        return BORROW.equals(normalize(type));
    }

    /** Loai nay la mot lan tra bot / thu bot goc cua mot khoan no da co. */
    public static boolean isSettlement(String type) {
        String t = normalize(type);
        return REPAY.equals(t) || COLLECT.equals(t);
    }

    /** Ten hien thi tieng Viet cua mot loai. */
    public static String typeName(String type) {
        String t = normalize(type);
        if (INCOME.equals(t)) return "Thu nh\u1eadp";
        if (BORROW.equals(t)) return "\u0110i vay";
        if (REPAY.equals(t)) return "Tr\u1ea3 n\u1ee3 g\u1ed1c";
        if (LEND.equals(t)) return "Cho vay";
        if (COLLECT.equals(t)) return "Thu h\u1ed3i n\u1ee3 g\u1ed1c";
        return "Chi ti\u00eau";
    }

    /** Ten ngan dung cho the loc va nut chon che do. */
    public static String typeShortName(String type) {
        String t = normalize(type);
        if (INCOME.equals(t)) return "Thu";
        if (BORROW.equals(t)) return "\u0110i vay";
        if (REPAY.equals(t)) return "Tr\u1ea3 n\u1ee3";
        if (LEND.equals(t)) return "Cho vay";
        if (COLLECT.equals(t)) return "Thu n\u1ee3";
        return "Chi";
    }

    /** Ky hieu hien o o tron ben trai moi dong danh sach. */
    public static String typeGlyph(String type) {
        String t = normalize(type);
        if (INCOME.equals(t)) return "\u2191";
        if (BORROW.equals(t)) return "\u21a9";
        if (REPAY.equals(t)) return "\u21aa";
        if (LEND.equals(t)) return "\u2197";
        if (COLLECT.equals(t)) return "\u2199";
        return "\u2193";
    }

    /**
     * Dau hien thi truoc so tien, bam theo tac dong len vi.
     * Di vay va thu hoi no hien dau +, tra no va cho vay hien dau -.
     */
    public static String typeSign(String type) {
        return walletSign(type) > 0 ? "+ " : "- ";
    }

    /** So tien co dau, dung de cong don ra so du vi. */
    public static double signedAmount(String type, double amount) {
        return walletSign(type) * amount;
    }

    // ------------------------------------------------------------- moc thoi gian

    public static long startOfMonth(int monthOffset) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, monthOffset);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static long endOfMonth(int monthOffset) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOfMonth(monthOffset));
        c.add(Calendar.MONTH, 1);
        return c.getTimeInMillis() - 1;
    }

    public static int daysLeftInMonth() {
        Calendar c = Calendar.getInstance();
        return c.getActualMaximum(Calendar.DAY_OF_MONTH) - c.get(Calendar.DAY_OF_MONTH);
    }

    // ------------------------------------------------------------- tong hop

    public static double totalExpense(List<TransactionEntity> list, long from, long to) {
        return totalOfType(list, EXPENSE, from, to);
    }

    /** Tong so tien cua mot loai bat ky trong khoang thoi gian. */
    public static double totalOfType(List<TransactionEntity> list, String type, long from, long to) {
        double sum = 0;
        if (list == null) return sum;
        for (TransactionEntity t : list) {
            if (type.equals(normalize(t.getType())) && t.getDate() >= from && t.getDate() <= to) {
                sum += t.getAmount();
            }
        }
        return sum;
    }

    /** Tong chi tieu theo danh muc, sap xep giam dan. Chi tinh EXPENSE. */
    public static List<Slice> byCategory(List<TransactionEntity> list, long from, long to) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (TransactionEntity t : list) {
            if (!EXPENSE.equals(normalize(t.getType()))) continue;
            if (t.getDate() < from || t.getDate() > to) continue;
            String key = t.getCategory() == null || t.getCategory().isEmpty() ? "Kh\u00e1c" : t.getCategory();
            Double old = map.get(key);
            map.put(key, (old == null ? 0d : old) + t.getAmount());
        }
        List<Slice> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : map.entrySet()) {
            out.add(new Slice(e.getKey(), e.getValue()));
        }
        Collections.sort(out, new Comparator<Slice>() {
            @Override
            public int compare(Slice a, Slice b) {
                return Double.compare(b.total, a.total);
            }
        });
        return out;
    }

    /** Gop thanh toi da 5 phan, phan con lai don vao "Khac" giong thiet ke. */
    public static List<Slice> topWithOther(List<Slice> slices, int max) {
        List<Slice> out = new ArrayList<>();
        double other = 0;
        for (int i = 0; i < slices.size(); i++) {
            if (i < max - 1) {
                out.add(slices.get(i));
            } else {
                other += slices.get(i).total;
            }
        }
        if (other > 0) out.add(new Slice("Kh\u00e1c", other));
        return out;
    }

    public static double changePercent(double now, double before) {
        if (before <= 0) return now > 0 ? 100 : 0;
        return (now - before) / before * 100d;
    }

    /** Cau mo ta bien dong: "tang 12,5%" hoac "giam 8%". */
    public static String changePhrase(double now, double before) {
        double change = changePercent(now, before);
        String direction = change >= 0 ? "t\u0103ng" : "gi\u1ea3m";
        return direction + " " + Money.percent(Math.abs(change)) + " so v\u1edbi k\u1ef3 tr\u01b0\u1edbc";
    }
}
