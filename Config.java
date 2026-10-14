package com.donutbed;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.BedItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Config {
    public static Config INSTANCE = new Config();

    public static final String[] BED_COLORS = { "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black" };

    /** One sellable item. match = "@bed" (any bed) or a registry id like "minecraft:diamond". */
    public static class ItemCfg {
        public String name = "Bed";
        public String match = "@bed";
        public String search = "bed";
        public long cost = 2500;
        public long minPrice = -1;  // -1 = use the global minimum
        public long undercut = -1;  // -1 = use the global undercut
        public boolean enabled = true;
        public int colorMask = 0;   // beds only: 0 = any color, else bit i = BED_COLORS[i]

        public boolean matches(ItemStack st) {
            if (st == null || st.isEmpty()) return false;
            if ("@bed".equals(match)) {
                if (!(st.getItem() instanceof BedItem)) return false;
                if (colorMask == 0) return true;
                String path = Registries.ITEM.getId(st.getItem()).getPath();
                for (int i = 0; i < BED_COLORS.length; i++) {
                    if (((colorMask >> i) & 1) != 0 && path.equals(BED_COLORS[i] + "_bed")) return true;
                }
                return false;
            }
            return Registries.ITEM.getId(st.getItem()).toString().equals(match);
        }

        public static ItemCfg bed(long cost) {
            ItemCfg b = new ItemCfg();
            b.cost = cost;
            return b;
        }
    }

    public boolean enabled = false;
    public boolean autoSell = true;
    public boolean notifications = true;
    public boolean autoPickBed = true;      // auto-pick item from inventory
    public int speed = 0;                    // 0 normal, 1 fast, 2 turbo
    public int priceCacheSec = 30;           // fast/turbo: reuse the scanned price this long
    public boolean autoSort = true;          // try the auction's sort button
    public int scanPages = 5;                // max pages to read if it can't sort
    public boolean perItemPrice = true;      // divide stack prices by stack size
    public boolean debug = false;
    public int theme = 0;
    public int undercutMode = 0;             // 0 = flat $, 1 = percent
    public double undercutPercent = 1.0;
    public long minProfit = 0;               // never list below cost + this
    public boolean dealFinder = true;
    public int dealPercent = 40;             // listing this % under market = deal
    public boolean outlierProtect = true;
    public int outlierPercent = 30;
    public boolean soundAlert = true;
    public boolean toastAlert = true;
    public String discordWebhook = "";
    public long undercut = 1000;
    public long minPrice = 0;
    public long bedCost = 2500;             // only used to migrate old configs
    public int scanDelayMs = 500;
    public int timeoutMs = 10000;
    public int cooldownMs = 5000;
    public boolean itemsMigrated = false;
    public List<ItemCfg> items = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("donut-bed-client.json");
    }

    public static Config load() {
        Config c = new Config();
        try {
            Path p = path();
            if (Files.exists(p)) {
                Config read = GSON.fromJson(Files.readString(p), Config.class);
                if (read != null) c = read;
            }
        } catch (Exception e) {
            System.err.println("[DonutBed] Could not read config, using defaults: " + e);
        }
        if (!c.itemsMigrated) {
            c.items = new ArrayList<>();
            c.items.add(ItemCfg.bed(c.bedCost));
            c.itemsMigrated = true;
        }
        c.sanitize();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this));
        } catch (Exception e) {
            System.err.println("[DonutBed] Could not save config: " + e);
        }
    }

    // --- speed helpers (Fast and Turbo override the slower timing settings) ---
    public int scanDelayEff() { return speed == 2 ? Math.min(scanDelayMs, 100) : speed == 1 ? Math.min(scanDelayMs, 250) : scanDelayMs; }
    public int cooldownEff() { return speed == 2 ? 1000 : speed == 1 ? 2000 : cooldownMs; }
    public int closeWait() { return speed == 2 ? 60 : speed == 1 ? 200 : 400; }
    public int cmdGap() { return speed == 2 ? 300 : speed == 1 ? 450 : 700; }
    public int verifyMs() { return speed == 2 ? 1200 : speed == 1 ? 1500 : 2000; }

    public void resetSettings() {
        Config d = new Config();
        autoSell = d.autoSell; autoPickBed = d.autoPickBed; speed = d.speed; priceCacheSec = d.priceCacheSec;
        undercut = d.undercut; undercutMode = d.undercutMode; undercutPercent = d.undercutPercent;
        minPrice = d.minPrice; minProfit = d.minProfit; outlierProtect = d.outlierProtect; outlierPercent = d.outlierPercent;
        autoSort = d.autoSort; perItemPrice = d.perItemPrice; scanPages = d.scanPages; scanDelayMs = d.scanDelayMs;
        timeoutMs = d.timeoutMs; cooldownMs = d.cooldownMs; debug = d.debug; dealFinder = d.dealFinder; dealPercent = d.dealPercent;
    }

    public long undercutFor(ItemCfg it) { return it.undercut >= 0 ? it.undercut : undercut; }
    public long minFor(ItemCfg it) { return it.minPrice >= 0 ? it.minPrice : minPrice; }

    public void sanitize() {
        undercut = Math.max(0, undercut);
        minPrice = Math.max(0, minPrice);
        bedCost = Math.max(0, bedCost);
        outlierPercent = Math.min(90, Math.max(5, outlierPercent));
        scanPages = Math.min(20, Math.max(1, scanPages));
        theme = Math.min(6, Math.max(0, theme));
        speed = Math.min(2, Math.max(0, speed));
        priceCacheSec = Math.min(300, Math.max(0, priceCacheSec));
        undercutMode = undercutMode == 1 ? 1 : 0;
        undercutPercent = Math.min(50.0, Math.max(0.0, undercutPercent));
        minProfit = Math.max(0, minProfit);
        dealPercent = Math.min(95, Math.max(5, dealPercent));
        scanDelayMs = Math.min(10000, Math.max(100, scanDelayMs));
        timeoutMs = Math.min(60000, Math.max(2000, timeoutMs));
        cooldownMs = Math.min(600000, Math.max(2000, cooldownMs));
        if (discordWebhook == null) discordWebhook = "";
        if (items == null) items = new ArrayList<>();
        if (items.isEmpty()) items.add(ItemCfg.bed(bedCost));
        for (ItemCfg it : items) {
            if (it.name == null || it.name.isBlank()) it.name = "Item";
            if (it.search == null) it.search = "";
            if (it.match == null) it.match = "@bed";
            it.cost = Math.max(0, it.cost);
        }
    }
}
