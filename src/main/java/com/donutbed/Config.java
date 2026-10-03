package com.donutbed;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public static Config INSTANCE = new Config();

    public boolean enabled = false;
    public boolean autoSell = true;
    public boolean repeat = false;
    public boolean notifications = true;
    public long undercut = 1000;
    public long minPrice = 0;
    public int scanDelayMs = 500;
    public int timeoutMs = 10000;
    public int cooldownMs = 5000;

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

    public void sanitize() {
        undercut = Math.max(0, undercut);
        minPrice = Math.max(0, minPrice);
        scanDelayMs = Math.min(10000, Math.max(100, scanDelayMs));
        timeoutMs = Math.min(60000, Math.max(2000, timeoutMs));
        cooldownMs = Math.min(600000, Math.max(2000, cooldownMs));
    }
}
