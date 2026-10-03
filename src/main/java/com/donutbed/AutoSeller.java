package com.donutbed;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BedItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.OptionalLong;

public final class AutoSeller {
    private AutoSeller() {}

    public enum Status { IDLE, SCANNING, PRICE_FOUND, SELLING, ERROR }

    public static Status status = Status.IDLE;
    public static long cheapest = -1;
    public static long sellPrice = -1;
    public static String message = "Ready.";

    private static long nextAllowed = 0;
    private static long stateStart = 0;
    private static long screenOpenedAt = -1;
    private static int consecutiveErrors = 0;
    private static boolean sold = false;
    private static long pendingSell = -1;

    public static void tick(MinecraftClient mc) {
        Config c = Config.INSTANCE;
        long now = System.currentTimeMillis();

        if (mc.player == null || mc.getNetworkHandler() == null) {
            if (status != Status.ERROR) status = Status.IDLE;
            return;
        }
        if (!c.enabled) {
            if (status != Status.ERROR) status = Status.IDLE;
            return;
        }

        switch (status) {
            case ERROR -> {
                if (now >= nextAllowed) status = Status.IDLE;
            }
            case IDLE -> {
                if (now >= nextAllowed && mc.currentScreen == null) start(mc, now);
            }
            case SCANNING -> {
                if (now - stateStart > c.timeoutMs) {
                    fail(mc, screenOpenedAt < 0
                            ? "Auction GUI did not open in time"
                            : "Empty /ah bed result (no listings loaded)");
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
                    if (now - stateStart > 1500) complete(mc, "Price found (Auto Sell is OFF, nothing sold)");
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
                    if (!(mc.player.getMainHandStack().getItem() instanceof BedItem)) {
                        fail(mc, "Hold the bed you want to sell in your main hand");
                        return;
                    }
                    if (pendingSell < Math.max(1, c.minPrice)) {
                        fail(mc, "Sell price below minimum - not selling");
                        return;
                    }
                    mc.getNetworkHandler().sendChatCommand("ah sell " + pendingSell);
                    sold = true;
                    stateStart = now;
                    message = "Sent /ah sell " + pendingSell;
                    notify(mc, "Sent: /ah sell " + pendingSell);
                } else if (now - stateStart > 1500) {
                    complete(mc, "Listed bed for $" + pendingSell);
                }
            }
        }
    }

    private static void start(MinecraftClient mc, long now) {
        Config c = Config.INSTANCE;
        c.sanitize();
        nextAllowed = now + c.cooldownMs;           // hard cooldown between auction commands
        cheapest = -1;
        sellPrice = -1;
        pendingSell = -1;
        screenOpenedAt = -1;
        stateStart = now;
        status = Status.SCANNING;
        message = "Sent /ah bed, waiting for GUI...";
        mc.getNetworkHandler().sendChatCommand("ah bed");
    }

    private static void scan(MinecraftClient mc, GenericContainerScreenHandler h, long now) {
        Config c = Config.INSTANCE;
        int stacks = 0, beds = 0, unpriced = 0;
        long min = Long.MAX_VALUE;

        for (Slot s : h.slots) {
            if (s.inventory instanceof PlayerInventory) continue; // only the auction container
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            stacks++;
            if (!(st.getItem() instanceof BedItem)) continue;     // real item check, not name
            OptionalLong p = PriceParser.fromStack(st, mc.player);
            if (p.isPresent()) {
                beds++;
                min = Math.min(min, p.getAsLong());
            } else {
                unpriced++;
            }
        }

        if (stacks == 0) {
            message = "Waiting for listings to load...";
            return; // keep waiting until timeout
        }
        if (beds == 0) {
            fail(mc, unpriced > 0 ? "Beds found but price could not be parsed" : "No bed listings detected");
            return;
        }

        cheapest = min;
        long target = min - c.undercut;
        sellPrice = target;
        long floor = Math.max(1, c.minPrice);
        if (target < floor) {
            fail(mc, "Calculated price $" + target + " is below minimum $" + floor);
            return;
        }

        pendingSell = target;
        status = Status.PRICE_FOUND;
        stateStart = now;
        message = beds + " bed listing(s). Cheapest $" + min + (unpriced > 0 ? " (" + unpriced + " unparsed ignored)" : "");
        mc.player.closeHandledScreen();
    }

    private static void complete(MinecraftClient mc, String msg) {
        Config c = Config.INSTANCE;
        consecutiveErrors = 0;
        status = Status.IDLE;
        message = msg;
        nextAllowed = Math.max(nextAllowed, System.currentTimeMillis() + c.cooldownMs);
        if (!c.repeat) {
            c.enabled = false;
            c.save();
        }
    }

    private static void fail(MinecraftClient mc, String msg) {
        Config c = Config.INSTANCE;
        status = Status.ERROR;
        message = msg;
        consecutiveErrors++;
        nextAllowed = Math.max(nextAllowed, System.currentTimeMillis() + c.cooldownMs);
        if (mc.player != null && mc.currentScreen instanceof HandledScreen<?>) mc.player.closeHandledScreen();
        notify(mc, "\u00a7cError: " + msg);
        if (!c.repeat || consecutiveErrors >= 3) {
            c.enabled = false; // stop instead of retrying forever
            c.save();
            if (c.repeat) notify(mc, "\u00a7cStopped after 3 errors in a row.");
        }
    }

    private static void notify(MinecraftClient mc, String msg) {
        if (Config.INSTANCE.notifications && mc.player != null) {
            mc.player.sendMessage(Text.literal("\u00a76[DonutBed] \u00a7r" + msg), false);
        }
    }
}
