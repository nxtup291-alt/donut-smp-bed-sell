package com.donutbed;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Remembers the market price of each item over time. File: config/donut-bed-prices.json */
public final class PriceLog {
    private PriceLog() {}

    public static class Point {
        public long t;
        public long p;
    }

    public static class Data {
        public Map<String, List<Point>> series = new LinkedHashMap<>();
    }

    private static Data data = new Data();
    private static final Map<String, Long> lastLogged = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().create();
    private static final int MAX_POINTS = 800;
    private static final long MIN_GAP_MS = 30_000;
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("MMM d h:mm a", Locale.US).withZone(ZoneId.systemDefault());

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("donut-bed-prices.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Data d = GSON.fromJson(Files.readString(p), Data.class);
                if (d != null && d.series != null) data = d;
            }
        } catch (Exception e) {
            System.err.println("[DonutBed] Could not read price log: " + e);
        }
    }

    private static void save() {
        try {
            Files.writeString(path(), GSON.toJson(data));
        } catch (Exception e) {
            System.err.println("[DonutBed] Could not save price log: " + e);
        }
    }

    public static void record(String item, long price) {
        long now = System.currentTimeMillis();
        Long last = lastLogged.get(item);
        if (last != null && now - last < MIN_GAP_MS) return;
        lastLogged.put(item, now);
        List<Point> list = data.series.computeIfAbsent(item, k -> new ArrayList<>());
        Point pt = new Point();
        pt.t = now;
        pt.p = price;
        list.add(pt);
        while (list.size() > MAX_POINTS) list.remove(0);
        save();
    }

    public static List<Point> points(String item, long sinceMs) {
        List<Point> out = new ArrayList<>();
        List<Point> list = data.series.get(item);
        if (list == null) return out;
        for (Point p : list) if (p.t >= sinceMs) out.add(p);
        return out;
    }

    public static String shortDate(long t) { return FMT.format(Instant.ofEpochMilli(t)); }
}
