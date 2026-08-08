package com.example.bmmoney.data;

import com.example.bmmoney.util.Stats;

/**
 * Mot khoan vay kem so con lai, tinh san trong SQLite.
 *
 * <p>Dung cho o chon "Tra cho khoan nao" khi ghi Tra no goc / Thu hoi no goc, va
 * cho the Bao cao cong no o man Phan tich.</p>
 */
public class LoanBalance {

    public String loanId;

    /** LEND (minh cho vay) hoac BORROW (minh di vay). */
    public String type;

    public String person;

    /** So tien goc ban dau. */
    public long principal;

    /** Tong da tra bot (REPAY) hoac da thu bot (COLLECT). */
    public long paid;

    /** Han tra / han doi, 0 la chua dat. */
    public long dueDate;

    public int writtenOff;

    public int settled;

    /** So con lai chua tat toan, khong bao gio am. */
    public long remaining() {
        long left = principal - paid;
        return left < 0 ? 0L : left;
    }

    /** Da dong chua: xoa so, danh dau tat toan, hoac da tra du goc. */
    public boolean isClosed() {
        return writtenOff == 1 || settled == 1 || remaining() <= 0L;
    }

    public boolean isReceivable() {
        return Stats.LEND.equals(type);
    }

    public String personOrUnknown() {
        return person == null || person.trim().isEmpty()
                ? PartnerEntity.UNKNOWN_LABEL : person;
    }

    /** Nhan hien thi trong danh sach chon khoan no. */
    public String label() {
        return Stats.typeName(type) + " \u00b7 " + personOrUnknown();
    }

    /** Giu ten cu de cac man hinh khong phai sua than ham. */
    public long getAmount() {
        return principal;
    }

    public String loanIdOrEmpty() {
        return loanId == null ? "" : loanId;
    }

    public long dueMillis() {
        return dueDate;
    }
}
