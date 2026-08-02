package com.example.bmmoney.data;

import com.example.bmmoney.util.Stats;

/**
 * Ban va 03/08. Mot khoan vay goc kem so con lai.
 *
 * <p>Dung cho o chon "Tra cho khoan nao" khi ghi Tra no goc / Thu hoi no goc,
 * va cho the Bao cao cong no o man Phan tich.</p>
 */
public class LoanBalance {

    public String loanId;
    /** BORROW (minh di vay) hoac LEND (minh cho vay). */
    public String type;
    public String person;
    /** So tien goc ban dau cua khoan vay. */
    public double principal;
    /** Tong da tra bot (REPAY) hoac da thu bot (COLLECT) cho khoan nay. */
    public double paid;
    public long dueDate;
    public int writtenOff;
    public int settled;

    /** So con lai chua tat toan, khong bao gio am. */
    public double remaining() {
        double left = principal - paid;
        return left < 0 ? 0d : left;
    }

    /** Khoan nay da dong chua: xoa so, danh dau tat toan, hoac tra du goc. */
    public boolean isClosed() {
        return writtenOff == 1 || settled == 1 || remaining() <= 0d;
    }

    public boolean isReceivable() {
        return Stats.LEND.equals(type);
    }

    public String personOrUnknown() {
        return person == null || person.trim().isEmpty() ? "Ch\u01b0a ghi t\u00ean" : person;
    }

    /** Nhan hien thi trong danh sach chon khoan no. */
    public String label() {
        return Stats.typeName(type) + " \u00b7 " + personOrUnknown();
    }
}
