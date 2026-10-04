package com.donutbed;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Saves every listed bed with date, price and profit. File: config/donut-bed-profit.json */
public final class Profit {
    private Profit() {}

    public static class Entry {
        public long time;
        public long price;
        public long cost;
        public long profit;
    }

    public static class Data {
        public List<Entry> entries = new ArrayList<>();
    }

    private static Data data = new Data();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a", Locale.US).withZone(ZoneId.systemDefault());

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("donut-bed-profit.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Data d = GSON.fromJson(Files.readString(p), Data.class);
                if (d != null && d.entries != null) data = d;
            }
        } catch (Exception e) {
            System.err.println("[DonutBed] Could not read profit file: " + e);
        }
    }

    private static void save() {
        try {
            Files.writeString(path(), GSON.toJson(data));
        } catch (Exception e) {
            System.err.println("[DonutBed] Could not save profit file: " + e);
        }
    }

    public static Entry record(long price, long cost) {
        Entry e = new Entry();
        e.time = System.currentTimeMillis();
        e.price = price;
        e.cost = cost;
        e.profit = price - cost;
        data.entries.add(e);
        save();
        return e;
    }

    public static void clear() {
        data.entries.clear();
        save();
    }

    public static List<Entry> entries() { return data.entries; }
    public static int count() { return data.entries.size(); }

    public static long total() { return since(0); }

    public static long since(long millis) {
        long t = 0;
        for (Entry e : data.entries) if (e.time >= millis) t += e.profit;
        return t;
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

    public static long average() {
        return data.entries.isEmpty() ? 0 : total() / data.entries.size();
    }

    public static String date(long t) { return FMT.format(Instant.ofEpochMilli(t)); }

    public static String money(long v) { return String.format("$%,d", v); }

    public static String signed(long v) { return (v >= 0 ? "+" : "-") + String.format("$%,d", Math.abs(v)); }

    /** Profit per day for the last N days (oldest first, today last). */
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

    public static String dayLabel(int daysAgo) {
        return LocalDate.now().minusDays(daysAgo).format(DateTimeFormatter.ofPattern("EEE", Locale.US));
    }
}
