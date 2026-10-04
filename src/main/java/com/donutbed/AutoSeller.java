package com.donutbed;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BedItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
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
    private static boolean sold = false;
    private static long pendingSell = -1;
    private static long lastListed = -1;
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
                if (!(mc.player.getMainHandStack().getItem() instanceof BedItem)) {
                    if (!c.autoPickBed) {
                        message = "Waiting - hold a bed in your main hand";
                        return;
                    }
                    if (now < invCooldown) return;
                    invCooldown = now + 400;
                    message = pickBed(mc) ? "Moved a bed into your hand" : "No bed found in your inventory";
                    return;
                }
                if (now < invCooldown) return; // let the inventory move sync with the server
                start(mc, now);
            }
            case SCANNING -> {
                if (now - stateStart > c.timeoutMs) {
                    fail(mc, screenOpenedAt < 0
                            ? "Auction GUI did not open in time"
                            : "No bed listings loaded - retrying");
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
                    if (!(mc.player.getMainHandStack().getItem() instanceof BedItem)) {
                        fail(mc, "Hold the bed you want to sell in your main hand");
                        return;
                    }
                    if (pendingSell < Math.max(1, c.minPrice)) {
                        complete("Skipped - price below your minimum");
                        return;
                    }
                    mc.getNetworkHandler().sendChatCommand("ah sell " + pendingSell);
                    sold = true;
                    lastListed = pendingSell;
                    stateStart = now;
                    Profit.Entry e = Profit.record(pendingSell, c.bedCost);
                    message = "Listed for " + Profit.money(pendingSell) + " (" + Profit.signed(e.profit) + ")";
                    notify(mc, "Listed " + Profit.money(pendingSell) + " | profit " + Profit.signed(e.profit)
                            + " | total " + Profit.signed(Profit.total()));
                } else if (now - stateStart > 1500) {
                    complete(message);
                }
            }
        }
    }

    /** Finds a bed in the hotbar (selects it) or main inventory (swaps it into the selected slot). */
    private static boolean pickBed(MinecraftClient mc) {
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() instanceof BedItem) {
                inv.selectedSlot = i;
                return true;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (inv.getStack(i).getItem() instanceof BedItem) {
                if (mc.interactionManager == null) return false;
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i,
                        inv.selectedSlot, SlotActionType.SWAP, mc.player);
                return true;
            }
        }
        return false;
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
        int stacks = 0, beds = 0, unpriced = 0, own = 0;
        String me = mc.player.getName().getString();
        long min = Long.MAX_VALUE;

        for (Slot s : h.slots) {
            if (s.inventory instanceof PlayerInventory) continue; // only the auction container
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            stacks++;
            if (!(st.getItem() instanceof BedItem)) continue;     // real item check, not name
            OptionalLong p = PriceParser.fromStack(st, mc.player);
            if (PriceParser.mentionsPlayer(st, mc.player, me)) { own++; continue; } // my own listing
            if (p.isPresent() && p.getAsLong() == lastListed) { own++; continue; }  // the one I just listed
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
            closeGui(mc);
            fail(mc, own > 0 && unpriced == 0 ? "Only your own bed listings found - not selling"
                    : unpriced > 0 ? "Beds found but price could not be parsed" : "No bed listings detected");
            return;
        }

        cheapest = min;
        long target = min - c.undercut;
        sellPrice = target;
        long floor = Math.max(1, c.minPrice);
        if (target < floor) {
            // Not an error: just skip this round and keep going.
            closeGui(mc);
            status = Status.IDLE;
            message = "Skipped: " + Profit.money(target) + " is below your " + Profit.money(floor) + " minimum";
            return;
        }

        pendingSell = target;
        status = Status.PRICE_FOUND;
        stateStart = now;
        message = beds + " bed listing(s). Cheapest " + Profit.money(min)
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

    private static void notify(MinecraftClient mc, String msg) {
        if (Config.INSTANCE.notifications && mc.player != null) {
            mc.player.sendMessage(Text.literal("\u00a76[DonutBed] \u00a7r" + msg), false);
        }
    }
}
