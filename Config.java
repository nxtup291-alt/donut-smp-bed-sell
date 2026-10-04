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

    /** One sellable item. match = "@bed" (any bed) or a registry id like "minecraft:diamond". */
    public static class ItemCfg {
        public String name = "Bed";
        public String match = "@bed";
        public String search = "bed";
        public long cost = 2500;
        public long minPrice = -1;  // -1 = use the global minimum
        public long undercut = -1;  // -1 = use the global undercut
        public boolean enabled = true;

        public boolean matches(ItemStack st) {
            if (st == null || st.isEmpty()) return false;
            if ("@bed".equals(match)) return st.getItem() instanceof BedItem;
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

    public long undercutFor(ItemCfg it) { return it.undercut >= 0 ? it.undercut : undercut; }
    public long minFor(ItemCfg it) { return it.minPrice >= 0 ? it.minPrice : minPrice; }

    public void sanitize() {
        undercut = Math.max(0, undercut);
        minPrice = Math.max(0, minPrice);
        bedCost = Math.max(0, bedCost);
        outlierPercent = Math.min(90, Math.max(5, outlierPercent));
        scanPages = Math.min(20, Math.max(1, scanPages));
        theme = Math.min(6, Math.max(0, theme));
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
