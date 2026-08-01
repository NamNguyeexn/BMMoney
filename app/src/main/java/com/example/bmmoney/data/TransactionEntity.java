package com.example.bmmoney.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Mot ban ghi tai chinh.
 *
 * <p>Ban va 02/08 them ba cot phuc vu phan Vay no / Tra no. Ba cot deu dung
 * kieu boc (String / Long / Integer) de cot SQLite la NULLABLE, nho vay
 * migration chi can ALTER TABLE ADD COLUMN ma khong phai khai bao default,
 * tranh loi "Migration didn't properly handle" cua Room.</p>
 *
 * <ul>
 *   <li>{@code person}  - ten nguoi vay (LEND) hoac nguoi minh phai tra (DEBT)</li>
 *   <li>{@code dueDate} - han doi / han phai tra, tinh bang mili giay</li>
 *   <li>{@code settled} - 1 la da tat toan, 0 hoac null la con treo</li>
 * </ul>
 */
@Entity(tableName = "transactions")
public class TransactionEntity {

    @PrimaryKey(autoGenerate = true) private int id;
    private String title;
    private double amount;
    private String type;
    private String category;
    private String note;
    private long date;

    private String person;
    private Long dueDate;
    private Integer settled;

    public TransactionEntity(String title, double amount, String type, String category,
                             String note, long date) {
        this.title = title;
        this.amount = amount;
        this.type = type;
        this.category = category;
        this.note = note;
        this.date = date;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }

    public String getPerson() { return person; }
    public void setPerson(String person) { this.person = person; }

    public Long getDueDate() { return dueDate; }
    public void setDueDate(Long dueDate) { this.dueDate = dueDate; }

    public Integer getSettled() { return settled; }
    public void setSettled(Integer settled) { this.settled = settled; }

    // ------------------------------------------------- tien ich doc cho de

    /** Han doi / han tra, tra ve 0 neu chua dat. */
    public long dueMillis() {
        return dueDate == null ? 0L : dueDate;
    }

    /** Da tat toan chua. */
    public boolean isSettled() {
        return settled != null && settled == 1;
    }

    /** Ten doi tac, tra ve chuoi rong neu chua dat. */
    public String personOrEmpty() {
        return person == null ? "" : person;
    }
}
