package com.marketscout;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public final class Alerts {
    private Alerts() {}

    public static void chat(MinecraftClient mc, String msg) {
        if (Config.INSTANCE.notifications && mc.player != null) {
            mc.player.sendMessage(Text.literal("\u00a76[Scout] \u00a7r" + msg), false);
        }
    }

    public static void fire(MinecraftClient mc, String title, String desc, String webhookText) {
        Config c = Config.INSTANCE;
        try {
            if (c.soundAlert) {
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F));
            }
            if (c.toastAlert) {
                SystemToast.add(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                        Text.literal(title), Text.literal(desc));
            }
        } catch (Throwable ignored) {}
        webhook(webhookText);
    }

    public static void test(MinecraftClient mc) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("\u00a76[Scout] \u00a7rTest alert"), false);
        fire(mc, "Market Scout", "Test alert - it works!", "Market Scout: test alert");
    }

    private static void webhook(String text) {
        String url = Config.INSTANCE.discordWebhook == null ? "" : Config.INSTANCE.discordWebhook.trim();
        if (!(url.startsWith("https://discord.com/api/webhooks/") || url.startsWith("https://discordapp.com/api/webhooks/"))) return;
        final String json = "{\"content\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        Thread t = new Thread(() -> {
            try {
                HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setRequestProperty("User-Agent", "MarketScout");
                con.setDoOutput(true);
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);
                try (OutputStream os = con.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                con.getResponseCode();
                con.disconnect();
            } catch (Exception ignored) {}
        }, "MarketScout-webhook");
        t.setDaemon(true);
        t.start();
    }
}
