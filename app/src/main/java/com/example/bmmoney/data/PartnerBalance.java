package com.example.bmmoney.data;

/**
 * So du cong no gop theo tung doi tac.
 *
 * <pre>
 * receivable = LEND   - COLLECT   ho dang no minh
 * payable    = BORROW - REPAY     minh dang no ho
 * net        = receivable - payable
 * </pre>
 */
public class PartnerBalance {

    /** {@code partners.id}. */
    public int partnerId;

    public String person;

    public long receivable;

    public long payable;

    /** Han gan nhat con treo, 0 la chua dat han nao. */
    public long nextDue;

    /** Duong la ho no minh, am la minh no ho. */
    public long net() {
        return receivable - payable;
    }

    public String personOrUnknown() {
        return person == null || person.trim().isEmpty()
                ? PartnerEntity.UNKNOWN_LABEL : person;
    }
}
