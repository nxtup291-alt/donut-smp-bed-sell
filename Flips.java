package com.marketscout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tracks your flips: what you bought and what you sold it for. File: config/market-scout-flips.json */
public final class Flips {
    private Flips() {}

    public static class Entry {
        public long time;
        public String item = "";
        public long buy;
        public long sell;
        public long profit;
    }

    public static class Open {
        public long time;
        public String item = "";
        public long price;
    }

    public static class Data {
        public List<Entry> entries = new ArrayList<>();
        public List<Open> open = new ArrayList<>();
    }

    private static Data data = new Data();
    private static final Gson GSON = new GsonBuilder().create();
    public static final long SESSION_START = System.currentTimeMillis();
    public static String lastNote = "";

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("market-scout-flips.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Data d = GSON.fromJson(Files.readString(p), Data.class);
                if (d != null) {
                    if (d.entries == null) d.entries = new ArrayList<>();
                    if (d.open == null) d.open = new ArrayList<>();
                    data = d;
                }
            }
        } catch (Exception e) {
            System.err.println("[MarketScout] Could not read flips file: " + e);
        }
    }

    private static void save() {
        try {
            Files.writeString(path(), GSON.toJson(data));
        } catch (Exception e) {
            System.err.println("[MarketScout] Could not save flips file: " + e);
        }
    }

    private static long net(long sell) {
        return Math.round(sell * (100.0 - Config.INSTANCE.saleTaxPercent) / 100.0);
    }

    public static void onBuy(String item, long price) {
        Open o = new Open();
        o.time = System.currentTimeMillis();
        o.item = item == null ? "" : item.trim();
        o.price = price;
        data.open.add(o);
        while (data.open.size() > 200) data.open.remove(0);
        save();
        lastNote = "Bought " + o.item + " " + Fmt.money(price);
    }

    /** Returns the finished flip, or null if there was no matching buy. */
    public static Entry onSell(String item, long price) {
        String key = item == null ? "" : item.trim().toLowerCase(Locale.ROOT);
        int found = -1;
        for (int i = 0; i < data.open.size(); i++) {
            String oi = data.open.get(i).item.toLowerCase(Locale.ROOT);
            if (key.isEmpty() || oi.isEmpty() || oi.contains(key) || key.contains(oi)) { found = i; break; }
        }
        if (found < 0) {
            lastNote = "Sale seen (" + Fmt.money(price) + ") but no matching buy. Add it manually.";
            return null;
        }
        Open o = data.open.remove(found);
        Entry e = addEntry(o.item.isEmpty() ? item : o.item, o.price, price);
        lastNote = "Flip done: " + e.item + " " + Fmt.signed(e.profit);
        return e;
    }

    public static Entry addManual(String item, long buy, long sell) {
        return addEntry(item, buy, sell);
    }

    private static Entry addEntry(String item, long buy, long sell) {
        Entry e = new Entry();
        e.time = System.currentTimeMillis();
        e.item = item == null || item.isBlank() ? "Item" : item.trim();
        e.buy = buy;
        e.sell = sell;
        e.profit = net(sell) - buy;
        data.entries.add(e);
        save();
        return e;
    }

    public static void clear() {
        data.entries.clear();
        data.open.clear();
        save();
    }

    public static List<Entry> entries() { return data.entries; }
    public static int openCount() { return data.open.size(); }
    public static int count() { return data.entries.size(); }

    public static long since(long millis) {
        long t = 0;
        for (Entry e : data.entries) if (e.time >= millis) t += e.profit;
        return t;
    }

    public static long total() { return since(0); }
    public static long session() { return since(SESSION_START); }

    public static long perHour() {
        double hours = Math.max(0.1, (System.currentTimeMillis() - SESSION_START) / 3_600_000.0);
        return Math.round(session() / hours);
    }

    public static long startOfToday() {
        return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static long best() {
        long b = 0;
        boolean first = true;
        for (Entry e : data.entries) {
            if (first || e.profit > b) { b = e.profit; first = false; }
        }
        return b;
    }

    public static long[] daily(int days) {
        long[] out = new long[days];
        LocalDate today = LocalDate.now();
        for (Entry e : data.entries) {
            LocalDate d = Instant.ofEpochMilli(e.time).atZone(ZoneId.systemDefault()).toLocalDate();
            long diff = ChronoUnit.DAYS.between(d, today);
            if (diff >= 0 && diff < days) out[days - 1 - (int) diff] += e.profit;
        }
        return out;
    }
}
