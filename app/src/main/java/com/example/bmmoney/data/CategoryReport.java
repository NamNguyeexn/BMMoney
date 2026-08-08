package com.example.bmmoney.data;

/**
 * Mot dong bao cao theo danh muc, lay bang JOIN giua transactions va categories.
 *
 * <p>Ten va emoji lay tu BANG DANH MUC chu khong phai tu chuoi chep tren giao dich,
 * nen doi ten danh muc la bao cao doi theo ngay lap tuc - khong con ban ghi mo coi
 * mang ten cu tach thanh mot dong rieng.</p>
 */
public class CategoryReport {

    /** {@code categories.id}. */
    public int categoryId;

    /** {@code categories.name}. */
    public String category;

    /** {@code categories.emoji}. */
    public String emoji;

    public long total;

    public int items;

    public String categoryOrEmpty() {
        return category == null ? "" : category;
    }

    public String emojiOrTag() {
        return emoji == null || emoji.isEmpty()
                ? CategoryEntity.FALLBACK_EMOJI : emoji;
    }

    /** "emoji ten", dung cho chu thich bieu do. */
    public String label() {
        return emojiOrTag() + " " + categoryOrEmpty();
    }
}
