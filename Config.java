package com.marketscout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Config {
    public static Config INSTANCE = new Config();

    /** One item on the watchlist. match = optional registry id filter, e.g. "minecraft:diamond". */
    public static class Watch {
        public String name = "Bed";
        public String search = "bed";
        public String match = "";
        public long alertBelow = 0;   // alert when the cheapest listing is at or below this (0 = off)
        public boolean enabled = true;

        public boolean matches(ItemStack st) {
            if (match == null || match.isEmpty()) return true;
            return Registries.ITEM.getId(st.getItem()).toString().equals(match);
        }
    }

    public boolean scouting = false;
    public boolean overlay = true;
    public boolean labels = true;
    public boolean autoSort = true;
    public boolean perItem = true;
    public int theme = 0;
    public int dealPercent = 35;
    public long minDealProfit = 1000;
    public long resellUndercut = 0;
    public double saleTaxPercent = 0;
    public int scanPages = 3;
    public int scanDelayMs = 500;
    public int timeoutMs = 10000;
    public int cooldownMs = 8000;
    public boolean notifications = true;
    public boolean soundAlert = true;
    public boolean toastAlert = true;
    public String discordWebhook = "";
    public boolean trackChat = true;
    public String buyRegex = "(?i)(?:you )?(?:bought|purchased)\\s+(?:\\d+x?\\s+)?(?<item>.+?)\\s+for\\s+\\$(?<price>[\\d,.]+[kmbt]?)";
    public String sellRegex = "(?i)(?<item>.+?)\\s+(?:has |was )?sold\\s+for\\s+\\$(?<price>[\\d,.]+[kmbt]?)";
    public List<Watch> watch = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("market-scout.json");
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
            System.err.println("[MarketScout] Could not read config, using defaults: " + e);
        }
        c.sanitize();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this));
        } catch (Exception e) {
            System.err.println("[MarketScout] Could not save config: " + e);
        }
    }

    public void sanitize() {
        theme = Math.min(6, Math.max(0, theme));
        dealPercent = Math.min(95, Math.max(5, dealPercent));
        minDealProfit = Math.max(0, minDealProfit);
        resellUndercut = Math.max(0, resellUndercut);
        saleTaxPercent = Math.min(90.0, Math.max(0.0, saleTaxPercent));
        scanPages = Math.min(20, Math.max(1, scanPages));
        scanDelayMs = Math.min(10000, Math.max(100, scanDelayMs));
        timeoutMs = Math.min(60000, Math.max(2000, timeoutMs));
        cooldownMs = Math.min(600000, Math.max(3000, cooldownMs));
        if (discordWebhook == null) discordWebhook = "";
        if (buyRegex == null) buyRegex = "";
        if (sellRegex == null) sellRegex = "";
        if (watch == null) watch = new ArrayList<>();
        if (watch.isEmpty()) watch.add(new Watch());
        for (Watch w : watch) {
            if (w.name == null || w.name.isBlank()) w.name = "Item";
            if (w.search == null) w.search = "";
            if (w.match == null) w.match = "";
            w.alertBelow = Math.max(0, w.alertBelow);
        }
    }
}
