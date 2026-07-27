package com.example.bmmoney.util;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Danh muc tuy chinh: them / sua / xoa, luu trong SharedPreferences. */
public final class Categories {

    private static final String ROW = "\n";
    private static final String SEP = "\u241f";

    private Categories() {
    }

    public static class Item {
        public String emoji;
        public String name;

        public Item(String emoji, String name) {
            this.emoji = emoji;
            this.name = name;
        }
    }

    private static List<Item> defaults() {
        List<Item> list = new ArrayList<>();
        list.add(new Item("\ud83c\udf5c", "\u0102n u\u1ed1ng"));
        list.add(new Item("\ud83d\ude97", "Di chuy\u1ec3n"));
        list.add(new Item("\ud83e\uddfe", "H\u00f3a \u0111\u01a1n"));
        list.add(new Item("\ud83d\udecd", "Mua s\u1eafm"));
        list.add(new Item("\ud83d\udc8a", "Y t\u1ebf"));
        list.add(new Item("\ud83c\udfac", "Gi\u1ea3i tr\u00ed"));
        return list;
    }

    public static List<Item> all(Context context) {
        String raw = Prefs.categoriesRaw(context);
        if (raw == null) return defaults();
        List<Item> list = new ArrayList<>();
        for (String line : raw.split(ROW)) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split(SEP, 2);
            if (parts.length == 2) {
                list.add(new Item(parts[0], parts[1]));
            }
        }
        return list;
    }

    public static void save(Context context, List<Item> list) {
        StringBuilder sb = new StringBuilder();
        for (Item item : list) {
            if (item.name == null || item.name.trim().isEmpty()) continue;
            sb.append(item.emoji == null || item.emoji.isEmpty() ? "\ud83c\udff7" : item.emoji)
                    .append(SEP).append(item.name.trim()).append(ROW);
        }
        Prefs.setCategoriesRaw(context, sb.toString());
    }

    public static String[] names(Context context) {
        List<Item> list = all(context);
        String[] out = new String[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i).name;
        return out;
    }
}
