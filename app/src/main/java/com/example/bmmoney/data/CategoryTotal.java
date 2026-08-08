package com.example.bmmoney.data;

/**
 * Tong chi tieu gop theo danh muc, dung cho bieu do tron va the so sanh ky.
 *
 * <p><b>Vi sao {@code total} van la {@code double} trong khi so tien trong bang da
 * doi sang {@code long}?</b></p>
 *
 * <p>Vi day khong phai so tien luu tru ma la so lieu bao cao. Cac man hinh do thang
 * gia tri nay vao {@code Map<String, Double>} roi chia ty le de ve bieu do. Neu doi
 * sang {@code long}, phep dong hop {@code long -> Double} khong ton tai trong Java
 * nen ba man hinh bao cao se gay bien dich ma khong doi lai duoc gi.</p>
 *
 * <p>Ranh gioi dat o dung cho: {@code long} cho tang luu tru va DAO, {@code double}
 * cho phep tinh ty le.</p>
 */
public class CategoryTotal {

    /** {@code categories.name}. */
    public String category;

    /** {@code categories.emoji}. */
    public String emoji;

    public double total;

    public int items;

    public String categoryOrOther() {
        return category == null || category.isEmpty() ? "Kh\u00e1c" : category;
    }

    public String emojiOrTag() {
        return emoji == null || emoji.isEmpty()
                ? CategoryEntity.FALLBACK_EMOJI : emoji;
    }
}
