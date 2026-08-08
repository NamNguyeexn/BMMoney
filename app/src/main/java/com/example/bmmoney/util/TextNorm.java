package com.example.bmmoney.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Chuan hoa chuoi de TIM KIEM duoc bang SQL.
 *
 * <p><b>Vi sao can lop nay?</b></p>
 *
 * <p>Lenh {@code LIKE} cua SQLite chi khong phan biet hoa - thuong voi chu ASCII.
 * Voi chu tieng Viet co dau thi {@code '\u0102n u\u1ed1ng' LIKE '%\u0103n%'} tra ve
 * SAI, va {@code '\u0102n u\u1ed1ng' LIKE '%an uong%'} cung tra ve SAI. Vi vay ban
 * cu buoc phai keo TOAN BO ket qua ve roi so tu khoa bang Java.</p>
 *
 * <p>Do la nguon goc that su cua loi "N ket qua nhung chi thay vai dong": khi loc
 * bang Java, bo dem tang cho MOI dong khop, con danh sach hien thi lai bi cat o
 * {@code limit}. Hai con so di tu hai duong khac nhau nen khong the nao khop.</p>
 *
 * <p>Cach chua: moi ban ghi luu san mot ban da bo dau, viet thuong. Cau truy van
 * so tren cot do, nen dem va lay trang chay CUNG mot dieu kien - va phan trang tro
 * lai dung.</p>
 *
 * <pre>
 * "\u0102n u\u1ed1ng s\u00e1ng"  ->  "an uong sang"
 * "C\u00e0 ph\u00ea Highlands"   ->  "ca phe highlands"
 * </pre>
 */
public final class TextNorm {

    private TextNorm() {
    }

    /**
     * Bo dau, viet thuong, gom khoang trang thua.
     *
     * <p>Chu <b>d</b> co gach ngang phai xu ly rieng: no KHONG phai la chu d cong
     * dau, ma la mot ky tu doc lap trong bang ma, nen buoc tach dau cua
     * {@link Normalizer} khong dong den no.</p>
     */
    public static String normalize(String value) {
        if (value == null) return "";
        String text = value.trim();
        if (text.isEmpty()) return "";

        text = text.toLowerCase(Locale.getDefault());
        text = text.replace('\u0111', 'd');

        // NFD tach mot chu co dau thanh "chu goc + dau roi", roi xoa cac dau roi.
        text = Normalizer.normalize(text, Normalizer.Form.NFD);
        text = text.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * Ghep nhieu manh thanh mot chuoi tim kiem duy nhat.
     *
     * <p>Bo qua manh rong nen khong sinh ra khoang trang thua giua cac tu.</p>
     */
    public static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        if (parts != null) {
            for (String part : parts) {
                String piece = normalize(part);
                if (piece.isEmpty()) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(piece);
            }
        }
        return sb.toString();
    }

    /**
     * Boc tu khoa thanh mau cho {@code LIKE}.
     *
     * <p>Tra ve {@code null} khi khong co tu khoa - cac cau truy van deu viet
     * {@code (:keyword IS NULL OR ...)} nen null nghia la bo qua dieu kien nay.</p>
     */
    public static String like(String keyword) {
        String key = normalize(keyword);
        return key.isEmpty() ? null : "%" + key + "%";
    }

    /**
     * Chi giu chu so, dung de tim theo so tien: go "250" ra khoan 250.000.
     *
     * <p>Tra ve {@code null} neu tu khoa khong chua chu so nao.</p>
     */
    public static String digitsLike(String keyword) {
        if (keyword == null) return null;
        String digits = keyword.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : "%" + digits + "%";
    }
}
