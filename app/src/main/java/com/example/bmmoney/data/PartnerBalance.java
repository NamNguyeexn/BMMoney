package com.example.bmmoney.data;

/**
 * Ban va 03/08. So du cong no gop theo tung doi tac.
 *
 * <p>Room do ket qua truy van GROUP BY vao lop nay. Quy uoc dau:</p>
 * <ul>
 *   <li>{@code receivable} = tong LEND - tong COLLECT: nguoi do con no minh</li>
 *   <li>{@code payable}    = tong BORROW - tong REPAY: minh con no nguoi do</li>
 *   <li>{@code net}        = receivable - payable, duong la ho no minh</li>
 * </ul>
 */
public class PartnerBalance {

    public String person;
    public double receivable;
    public double payable;
    /** Han gan nhat con treo cua doi tac nay, 0 la chua dat han nao. */
    public long nextDue;

    /** So du rong: duong = ho no minh, am = minh no ho. */
    public double net() {
        return receivable - payable;
    }

    /** Ten hien thi, tranh de trong tren giao dien. */
    public String personOrUnknown() {
        return person == null || person.trim().isEmpty() ? "Ch\u01b0a ghi t\u00ean" : person;
    }
}
