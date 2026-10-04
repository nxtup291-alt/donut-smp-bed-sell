package com.donutbed;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;

public final class AutoSeller {
    private AutoSeller() {}

    public enum Status { IDLE, SCANNING, PRICE_FOUND, SELLING, ERROR }

    public static Status status = Status.IDLE;
    public static long cheapest = -1;
    public static long sellPrice = -1;
    public static String message = "Ready.";
    public static String currentName = "-";

    private static Config.ItemCfg current = null;
    private static int rot = 0;
    private static long nextAllowed = 0;
    private static long stateStart = 0;
    private static long screenOpenedAt = -1;
    private static boolean sold = false;
    private static long pendingSell = -1;
    private static long lastListed = -1;
    private static String lastListedItem = "";
    private static String lastNotified = "";
    private static long invCooldown = 0;

    public static void tick(MinecraftClient mc) {
        Config c = Config.INSTANCE;
        long now = System.currentTimeMillis();

        if (mc.player == null || mc.getNetworkHandler() == null || !c.enabled) {
            status = Status.IDLE;
            return;
        }

        switch (status) {
            case ERROR -> {
                if (now >= nextAllowed) status = Status.IDLE;
            }
            case IDLE -> {
                if (now < nextAllowed || mc.currentScreen != null) return;
                Config.ItemCfg it = chooseItem(mc, c);
                if (it == null) return;
                if (!it.matches(mc.player.getMainHandStack())) {
                    if (now < invCooldown) return;
                    invCooldown = now + 400;
                    message = pick(mc, it) ? "Moved " + it.name + " into your hand" : "No " + it.name + " in your inventory";
                    return;
                }
                if (now < invCooldown) return; // let the inventory move sync with the server
                start(mc, now, it);
            }
            case SCANNING -> {
                if (now - stateStart > c.timeoutMs) {
                    fail(mc, screenOpenedAt < 0
                            ? "Auction GUI did not open in time"
                            : "No listings loaded - retrying");
                    return;
                }
                if (mc.currentScreen instanceof HandledScreen<?> hs
                        && hs.getScreenHandler() instanceof GenericContainerScreenHandler h) {
                    if (screenOpenedAt < 0) {
                        screenOpenedAt = now;
                        message = "Auction GUI open, scanning...";
                        return;
                    }
                    if (now - screenOpenedAt >= c.scanDelayMs) scan(mc, h, now);
                }
            }
            case PRICE_FOUND -> {
                if (now - stateStart < 400) return; // let the GUI close
                if (!c.autoSell) {
                    if (now - stateStart > 1500) complete("Price found (Auto Sell is OFF, nothing sold)");
                    return;
                }
                status = Status.SELLING;
                sold = false;
                stateStart = now;
            }
            case SELLING -> {
                if (!sold) {
                    if (mc.currentScreen != null) {
                        if (now - stateStart > 3000) fail(mc, "Could not close the auction GUI");
                        return;
                    }
                    if (current == null || !current.matches(mc.player.getMainHandStack())) {
                        fail(mc, "The item to sell is no longer in your main hand");
                        return;
                    }
                    if (pendingSell < Math.max(1, c.minFor(current))) {
                        complete("Skipped - price below your minimum");
                        return;
                    }
                    mc.getNetworkHandler().sendChatCommand("ah sell " + pendingSell);
                    sold = true;
                    lastListed = pendingSell;
                    lastListedItem = current.name;
                    stateStart = now;
                    Profit.Entry e = Profit.record(current.name, pendingSell, current.cost);
                    message = "Listed " + current.name + " for " + Profit.money(pendingSell) + " (" + Profit.signed(e.profit) + ")";
                    alertListed(mc, current, pendingSell, e);
                } else if (now - stateStart > 1500) {
                    complete(message);
                }
            }
        }
    }

    // ------------------------------------------------------------ item selection

    private static Config.ItemCfg chooseItem(MinecraftClient mc, Config c) {
        List<Config.ItemCfg> items = c.items;
        int n = items.size();
        if (!c.autoPickBed) {
            ItemStack held = mc.player.getMainHandStack();
            for (Config.ItemCfg it : items) {
                if (it.enabled && it.matches(held)) return it;
            }
            message = "Hold one of your enabled items in your main hand";
            return null;
        }
        for (int k = 0; k < n; k++) {
            Config.ItemCfg it = items.get((rot + k) % n);
            if (it.enabled && hasItem(mc, it)) return it;
        }
        message = "Nothing to sell - none of your enabled items are in your inventory";
        return null;
    }

    private static boolean hasItem(MinecraftClient mc, Config.ItemCfg it) {
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (it.matches(inv.getStack(i))) return true;
        }
        return false;
    }

    /** Selects the item from the hotbar, or swaps it from the main inventory into the selected slot. */
    private static boolean pick(MinecraftClient mc, Config.ItemCfg it) {
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (it.matches(inv.getStack(i))) {
                inv.selectedSlot = i;
                return true;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (it.matches(inv.getStack(i))) {
                if (mc.interactionManager == null) return false;
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i,
                        inv.selectedSlot, SlotActionType.SWAP, mc.player);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ cycle

    private static void start(MinecraftClient mc, long now, Config.ItemCfg it) {
        Config c = Config.INSTANCE;
        c.sanitize();
        String term = it.search == null ? "" : it.search.replaceAll("[\\r\\n/]", " ").trim();
        if (term.isEmpty()) {
            current = it;
            currentName = it.name;
            nextAllowed = now + c.cooldownMs;
            fail(mc, it.name + " has no search term (set one in the Items tab)");
            return;
        }
        current = it;
        currentName = it.name;
        int idx = c.items.indexOf(it);
        rot = (idx + 1) % Math.max(1, c.items.size());
        nextAllowed = now + c.cooldownMs;           // hard cooldown between auction commands
        cheapest = -1;
        sellPrice = -1;
        pendingSell = -1;
        screenOpenedAt = -1;
        stateStart = now;
        status = Status.SCANNING;
        message = "Sent /ah " + term + ", waiting for GUI...";
        mc.getNetworkHandler().sendChatCommand("ah " + term);
    }

    private static void scan(MinecraftClient mc, GenericContainerScreenHandler h, long now) {
        Config c = Config.INSTANCE;
        Config.ItemCfg it = current;
        int stacks = 0, unpriced = 0, own = 0, lowball = 0;
        List<Long> prices = new ArrayList<>();
        String me = mc.player.getName().getString();

        for (Slot s : h.slots) {
            if (s.inventory instanceof PlayerInventory) continue; // only the auction container
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            stacks++;
            if (!it.matches(st)) continue;                         // real item check, not name
            OptionalLong p = PriceParser.fromStack(st, mc.player);
            if (PriceParser.mentionsPlayer(st, mc.player, me)) { own++; continue; } // my own listing
            if (p.isPresent() && p.getAsLong() == lastListed && it.name.equals(lastListedItem)) { own++; continue; }
            if (p.isPresent()) prices.add(p.getAsLong());
            else unpriced++;
        }

        if (stacks == 0) {
            message = "Waiting for listings to load...";
            return; // keep waiting until timeout
        }
        if (prices.isEmpty()) {
            closeGui(mc);
            fail(mc, own > 0 && unpriced == 0 ? "Only your own " + it.name + " listings found - not selling"
                    : unpriced > 0 ? it.name + " found but price could not be parsed" : "No " + it.name + " listings detected");
            return;
        }

        Collections.sort(prices);
        int total = prices.size();
        if (c.outlierProtect) {
            // Drop absurdly low listings so one troll listing can't drag the price down.
            while (prices.size() >= 2 && lowball < 3
                    && prices.get(0) * 100 < prices.get(1) * (100L - c.outlierPercent)) {
                prices.remove(0);
                lowball++;
            }
        }
        long ref = prices.get(0);
        PriceLog.record(it.name, ref);

        cheapest = ref;
        long target = ref - c.undercutFor(it);
        sellPrice = target;
        long floor = Math.max(1, c.minFor(it));
        if (target < floor) {
            // Not an error: skip this round and keep going.
            closeGui(mc);
            status = Status.IDLE;
            message = "Skipped " + it.name + ": " + Profit.money(target) + " is below your " + Profit.money(floor) + " minimum";
            return;
        }

        pendingSell = target;
        status = Status.PRICE_FOUND;
        stateStart = now;
        message = total + " " + it.name + " listing(s). Price " + Profit.money(ref)
                + (lowball > 0 ? " [ignored " + lowball + " lowball]" : "")
                + (unpriced > 0 ? " (" + unpriced + " unparsed ignored)" : "")
                + (own > 0 ? " [ignored " + own + " of mine]" : "");
        closeGui(mc);
    }

    private static void closeGui(MinecraftClient mc) {
        if (mc.player != null && mc.currentScreen instanceof HandledScreen<?>) mc.player.closeHandledScreen();
    }

    private static void complete(String msg) {
        status = Status.IDLE;
        message = msg;
        lastNotified = "";
        nextAllowed = Math.max(nextAllowed, System.currentTimeMillis() + Config.INSTANCE.cooldownMs);
    }

    private static void fail(MinecraftClient mc, String msg) {
        status = Status.ERROR;
        message = msg;
        nextAllowed = Math.max(nextAllowed, System.currentTimeMillis() + Config.INSTANCE.cooldownMs);
        closeGui(mc);
        if (!msg.equals(lastNotified)) { // don't repeat the same chat message every cycle
            lastNotified = msg;
            notify(mc, "\u00a7c" + msg + " (will keep trying)");
        }
    }

    // ------------------------------------------------------------ alerts

    private static void alertListed(MinecraftClient mc, Config.ItemCfg it, long price, Profit.Entry e) {
        String msg = "Listed " + it.name + " for " + Profit.money(price) + " | profit " + Profit.signed(e.profit)
                + " | total " + Profit.signed(Profit.total());
        notify(mc, msg);
        fireAlerts(mc, "Listed " + it.name + " " + Profit.money(price), "Profit " + Profit.signed(e.profit)
                + "  |  Total " + Profit.signed(Profit.total()), msg);
    }

    public static void testAlert(MinecraftClient mc) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("\u00a76[DonutBed] \u00a7rTest alert"), false);
        }
        fireAlerts(mc, "Donut Bed Client", "Test alert - it works!", "Donut Bed Client: test alert");
    }

    private static void fireAlerts(MinecraftClient mc, String title, String desc, String webhookText) {
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
        sendWebhook(webhookText);
    }

    private static void sendWebhook(String text) {
        String url = Config.INSTANCE.discordWebhook == null ? "" : Config.INSTANCE.discordWebhook.trim();
        if (!(url.startsWith("https://discord.com/api/webhooks/") || url.startsWith("https://discordapp.com/api/webhooks/"))) return;
        final String json = "{\"content\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        Thread t = new Thread(() -> {
            try {
                HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setRequestProperty("User-Agent", "DonutBedClient");
                con.setDoOutput(true);
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);
                try (OutputStream os = con.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                con.getResponseCode();
                con.disconnect();
            } catch (Exception ignored) {}
        }, "DonutBed-webhook");
        t.setDaemon(true);
        t.start();
    }

    private static void notify(MinecraftClient mc, String msg) {
        if (Config.INSTANCE.notifications && mc.player != null) {
            mc.player.sendMessage(Text.literal("\u00a76[DonutBed] \u00a7r" + msg), false);
        }
    }
}
