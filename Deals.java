package com.marketscout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Listings found far below market. File: config/market-scout-deals.json */
public final class Deals {
    private Deals() {}

    public static class Entry {
        public long time;
        public String item = "";
        public long price;
        public long resell;
        public long potential;
        public int discount;
    }

    public static class Data {
        public List<Entry> entries = new ArrayList<>();
    }

    private static Data data = new Data();
    private static final Gson GSON = new GsonBuilder().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("market-scout-deals.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Data d = GSON.fromJson(Files.readString(p), Data.class);
                if (d != null && d.entries != null) data = d;
            }
        } catch (Exception e) {
            System.err.println("[MarketScout] Could not read deals file: " + e);
        }
    }

    private static void save() {
        try {
            Files.writeString(path(), GSON.toJson(data));
        } catch (Exception e) {
            System.err.println("[MarketScout] Could not save deals file: " + e);
        }
    }

    public static void record(String item, long price, long resell, long potential, int discount) {
        Entry e = new Entry();
        e.time = System.currentTimeMillis();
        e.item = item;
        e.price = price;
        e.resell = resell;
        e.potential = potential;
        e.discount = discount;
        data.entries.add(e);
        while (data.entries.size() > 150) data.entries.remove(0);
        save();
    }

    public static void clear() {
        data.entries.clear();
        save();
    }

    public static List<Entry> entries() { return data.entries; }
}
