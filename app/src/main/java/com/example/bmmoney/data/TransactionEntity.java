package com.example.bmmoney.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions")
public class TransactionEntity {
    @PrimaryKey(autoGenerate = true) private int id;
    private String title; private double amount; private String type; private String category; private String note; private long date;
    public TransactionEntity(String title, double amount, String type, String category, String note, long date) { this.title = title; this.amount = amount; this.type = type; this.category = category; this.note = note; this.date = date; }
    public int getId() { return id; } public void setId(int id) { this.id = id; }
    public String getTitle() { return title; } public void setTitle(String title) { this.title = title; }
    public double getAmount() { return amount; } public void setAmount(double amount) { this.amount = amount; }
    public String getType() { return type; } public void setType(String type) { this.type = type; }
    public String getCategory() { return category; } public void setCategory(String category) { this.category = category; }
    public String getNote() { return note; } public void setNote(String note) { this.note = note; }
    public long getDate() { return date; } public void setDate(long date) { this.date = date; }
}
