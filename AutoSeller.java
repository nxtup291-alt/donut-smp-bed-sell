package com.donutbed;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AutoSeller {
    private AutoSeller() {}

    public enum Status { IDLE, SCANNING, PRICE_FOUND, SELLING, ERROR }

    private enum Action { SORT, NEXT }

    /** One priced listing: unit = price per item, raw = price shown, count = stack size. */
    private record Cand(long unit, long raw, int count, PriceParser.Result res) {}

    public static Status status = Status.IDLE;
    public static long cheapest = -1;
    public static long sellPrice = -1;
    public static String message = "Ready.";
    public static String currentName = "-";

    private static Config.ItemCfg current = null;
    private static int rot = 0;
    public static long nextAllowed = 0;
    private static final Map<String, Long> dealSeen = new HashMap<>();
    private static long stateStart = 0;
    private static long screenOpenedAt = -1;
    private static boolean sold = false;
    private static long pendingSell = -1; // price per item
    private static long lastListed = -1;
    private static String lastListedItem = "";
    private static String lastNotified = "";
    private static long invCooldown = 0;

    // scanning state
    private static final List<Cand> acc = new ArrayList<>();
    private static int accOwn, accUnpriced, pagesScanned, sortClicks;
    private static boolean sortedMode, sortGiveUp, awaiting;
    private static Action lastAction = Action.NEXT;
    private static String lastSnap = "";
    private static int waitIgnore = -1;
    private static long actionAt;

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
                    if (!acc.isEmpty()) { // use whatever pages we managed to read
                        finishScan(mc, now);
                        return;
                    }
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
                    ItemStack held = mc.player.getMainHandStack();
                    if (current == null || !current.matches(held)) {
                        fail(mc, "The item to sell is no longer in your main hand");
                        return;
                    }
                    if (pendingSell < Math.max(1, c.minFor(current))) {
                        complete("Skipped - price below your minimum");
                        return;
                    }
                    int count = Math.max(1, held.getCount());
                    long total = c.perItemPrice ? pendingSell * count : pendingSell;
                    mc.getNetworkHandler().sendChatCommand("ah sell " + total);
                    sold = true;
                    lastListed = total;
                    lastListedItem = current.name;
                    stateStart = now;
                    Profit.Entry e = Profit.record(current.name, total, current.cost * count);
                    message = "Listed " + (count > 1 ? count + "x " : "") + current.name + " for "
                            + Profit.money(total) + " (" + Profit.signed(e.profit) + ")";
                    alertListed(mc, current, total, e);
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
        current = it;
        currentName = it.name;
        if (term.isEmpty()) {
            nextAllowed = now + c.cooldownMs;
            fail(mc, it.name + " has no search term (set one in the Items tab)");
            return;
        }
        int idx = c.items.indexOf(it);
        rot = (idx + 1) % Math.max(1, c.items.size());
        nextAllowed = now + c.cooldownMs;           // hard cooldown between auction commands
        cheapest = -1;
        sellPrice = -1;
        pendingSell = -1;
        screenOpenedAt = -1;
        stateStart = now;
        acc.clear();
        accOwn = 0;
        accUnpriced = 0;
        pagesScanned = 0;
        sortClicks = 0;
        sortedMode = false;
        sortGiveUp = false;
        awaiting = false;
        status = Status.SCANNING;
        message = "Sent /ah " + term + ", waiting for GUI...";
        mc.getNetworkHandler().sendChatCommand("ah " + term);
    }

    private static String snapshot(GenericContainerScreenHandler h, int ignore) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < h.slots.size(); i++) {
            if (i == ignore) continue; // the slot we clicked may change locally; don't count it
            Slot s = h.slots.get(i);
            if (s.inventory instanceof PlayerInventory) continue;
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            sb.append(i).append(':').append(Registries.ITEM.getId(st.getItem()).getPath()).append('x').append(st.getCount());
            LoreComponent lore = st.get(DataComponentTypes.LORE);
            if (lore != null) {
                int hash = 0;
                for (Text t : lore.lines()) hash = hash * 31 + t.getString().hashCode();
                sb.append('#').append(hash);
            }
            sb.append(';');
        }
        return sb.toString();
    }

    private static boolean ascending(List<Cand> page) {
        for (int i = 1; i < page.size(); i++) {
            if (page.get(i).unit() < page.get(i - 1).unit()) return false;
        }
        return true;
    }

    private static void clickGui(MinecraftClient mc, GenericContainerScreenHandler h, int idx, Action a, long now) {
        if (mc.interactionManager == null) return;
        awaiting = true;
        lastAction = a;
        waitIgnore = idx;
        lastSnap = snapshot(h, idx);
        actionAt = now;
        screenOpenedAt = now;   // settle delay again
        stateStart = now;       // fresh timeout for this step
        mc.interactionManager.clickSlot(h.syncId, idx, 0, SlotActionType.PICKUP, mc.player);
    }

    private static void scan(MinecraftClient mc, GenericContainerScreenHandler h, long now) {
        Config c = Config.INSTANCE;
        Config.ItemCfg it = current;
        String me = mc.player.getName().getString();

        // Waiting for a sort/next click to take effect?
        if (awaiting) {
            String snap = snapshot(h, waitIgnore);
            if (snap.equals(lastSnap) && now - actionAt < 2500) return;
            boolean changed = !snap.equals(lastSnap);
            awaiting = false;
            if (!changed) {
                if (lastAction == Action.NEXT) { // no more pages
                    finishScan(mc, now);
                    return;
                }
                sortGiveUp = true;              // sort button did nothing
            }
        }

        List<Cand> page = new ArrayList<>();
        int stacks = 0, unpriced = 0, own = 0, nextIdx = -1, sortIdx = -1;
        for (int i = 0; i < h.slots.size(); i++) {
            Slot s = h.slots.get(i);
            if (s.inventory instanceof PlayerInventory) continue; // only the auction container
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            stacks++;
            if (it.matches(st)) {                                  // real item check, not name
                PriceParser.Result r = PriceParser.analyze(st, mc.player);
                if (PriceParser.mentionsPlayer(st, mc.player, me)) { own++; continue; } // my own listing
                if (r == null) { unpriced++; continue; }
                if (r.price() == lastListed && it.name.equals(lastListedItem)) { own++; continue; }
                int cnt = Math.max(1, st.getCount());
                long unit = (c.perItemPrice && !r.perUnit()) ? r.price() / cnt : r.price();
                if (unit <= 0) { unpriced++; continue; }
                page.add(new Cand(unit, r.price(), cnt, r));
            } else if (nextIdx < 0 || sortIdx < 0) {
                String nm = PriceParser.clean(st.getName().getString()).toLowerCase(Locale.ROOT);
                boolean isNext = nm.contains("next");
                boolean isSort = nm.contains("sort");
                if (isNext || isSort) {
                    // Safety: never click anything that looks like a priced listing.
                    if (PriceParser.analyze(st, mc.player) == null) {
                        if (isNext && nextIdx < 0) nextIdx = i;
                        else if (isSort && sortIdx < 0) sortIdx = i;
                    }
                }
            }
        }

        if (stacks == 0) {
            if (pagesScanned == 0) {
                message = "Waiting for listings to load...";
                return; // keep waiting until timeout
            }
            finishScan(mc, now); // empty page = end of results
            return;
        }

        // First page: is it already sorted lowest-first? If not, try the sort button.
        if (pagesScanned == 0 && c.autoSort && !sortedMode && !sortGiveUp && page.size() >= 3) {
            if (ascending(page)) {
                sortedMode = true;
            } else if (sortIdx >= 0 && sortClicks < 6) {
                if (!h.getCursorStack().isEmpty()) { message = "Waiting for GUI..."; return; }
                sortClicks++;
                message = "Sorting by lowest price... (" + sortClicks + ")";
                clickGui(mc, h, sortIdx, Action.SORT, now);
                return;
            }
        }

        boolean wantNext = !sortedMode && (pagesScanned + 1) < c.scanPages && nextIdx >= 0;
        if (wantNext && !h.getCursorStack().isEmpty()) { message = "Waiting for GUI..."; return; }

        acc.addAll(page);
        accOwn += own;
        accUnpriced += unpriced;
        pagesScanned++;

        if (wantNext) {
            message = "Reading page " + (pagesScanned + 1) + "...";
            clickGui(mc, h, nextIdx, Action.NEXT, now);
            return;
        }
        finishScan(mc, now);
    }

    private static void finishScan(MinecraftClient mc, long now) {
        Config c = Config.INSTANCE;
        Config.ItemCfg it = current;
        awaiting = false;

        if (acc.isEmpty()) {
            closeGui(mc);
            fail(mc, accOwn > 0 && accUnpriced == 0 ? "Only your own " + it.name + " listings found - not selling"
                    : accUnpriced > 0 ? it.name + " found but price could not be parsed" : "No " + it.name + " listings detected");
            return;
        }

        List<Cand> list = new ArrayList<>(acc);
        list.sort(Comparator.comparingLong(Cand::unit));
        int total = list.size();
        if (c.dealFinder) detectDeals(mc, it, new ArrayList<>(list));
        int lowball = 0;
        if (c.outlierProtect) {
            // Drop absurdly low listings so one troll listing can't drag the price down.
            while (list.size() >= 2 && lowball < 3
                    && list.get(0).unit() * 100 < list.get(1).unit() * (100L - c.outlierPercent)) {
                list.remove(0);
                lowball++;
            }
        }
        Cand best = list.get(0);
        long ref = best.unit();
        PriceLog.record(it.name, ref);

        if (c.debug) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(8, list.size()); i++) {
                if (i > 0) sb.append(", ");
                sb.append(Profit.compact(list.get(i).unit()));
            }
            debug(mc, "pages read: " + pagesScanned + (sortedMode ? " (list is sorted)" : "") + ", listings: " + total);
            debug(mc, "lowest prices (per item): " + sb);
            debug(mc, "used " + Profit.money(ref) + " from line \"" + best.res().line() + "\" (shown " + Profit.money(best.raw())
                    + ", stack " + best.count() + ")");
            String lore = String.join(" | ", best.res().lines());
            debug(mc, "tooltip: " + (lore.length() > 260 ? lore.substring(0, 260) + "..." : lore));
        }

        cheapest = ref;
        long target = ref - undercutAmount(c, it, ref);
        sellPrice = target;
        long floor = Math.max(Math.max(1, c.minFor(it)), it.cost + c.minProfit);
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
        message = total + " " + it.name + " listing(s), " + pagesScanned + " page(s). Price " + Profit.money(ref)
                + (lowball > 0 ? " [ignored " + lowball + " lowball]" : "")
                + (accUnpriced > 0 ? " (" + accUnpriced + " unparsed ignored)" : "")
                + (accOwn > 0 ? " [ignored " + accOwn + " of mine]" : "");
        closeGui(mc);
    }

    /** How much to undercut by: per-item override, else percent or flat from the settings. */
    public static long undercutAmount(Config c, Config.ItemCfg it, long ref) {
        if (it.undercut >= 0) return it.undercut;
        if (c.undercutMode == 1) return Math.max(1, Math.round(ref * c.undercutPercent / 100.0));
        return c.undercut;
    }

    /** Finds listings far below the market and tells you about them (it never buys anything). */
    private static void detectDeals(MinecraftClient mc, Config.ItemCfg it, List<Cand> sorted) {
        Config c = Config.INSTANCE;
        if (sorted.size() < 3) return;
        long median = sorted.get(sorted.size() / 2).unit();
        long limit = median * (100 - c.dealPercent) / 100;
        long resell = median;
        for (Cand cand : sorted) {
            if (cand.unit() > limit) { resell = cand.unit(); break; }
        }
        long now = System.currentTimeMillis();
        int alerts = 0;
        for (Cand cand : sorted) {
            if (cand.unit() > limit || alerts >= 3) break;
            long potential = resell - undercutAmount(c, it, resell) - cand.unit();
            if (potential <= 0) continue;
            String key = it.name + ":" + cand.raw() + ":" + cand.count();
            Long seen = dealSeen.get(key);
            if (seen != null && now - seen < 600_000L) continue;
            dealSeen.put(key, now);
            alerts++;
            Deals.record(it.name, cand.unit(), resell, potential);
            String msg = "DEAL: " + it.name + " " + Profit.money(cand.unit()) + " (resell ~" + Profit.money(resell)
                    + ", about +" + Profit.money(potential) + ")";
            notify(mc, "\u00a7a" + msg);
            fireAlerts(mc, "DEAL: " + it.name + " " + Profit.money(cand.unit()),
                    "Resell ~" + Profit.money(resell) + "  |  +" + Profit.money(potential), msg);
        }
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

    private static void debug(MinecraftClient mc, String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("\u00a77[DonutBed debug] " + msg), false);
        }
    }

    private static void notify(MinecraftClient mc, String msg) {
        if (Config.INSTANCE.notifications && mc.player != null) {
            mc.player.sendMessage(Text.literal("\u00a76[DonutBed] \u00a7r" + msg), false);
        }
    }
}
