package com.marketscout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Cycles through your watchlist: /ah <item>, reads every listing, ranks flips, fires alerts. Never buys anything. */
public final class Scout {
    private Scout() {}

    public enum Status { IDLE, SCANNING, ERROR }

    private enum Action { SORT, NEXT }

    private record Cand(long unit, long raw, int count) {}

    public static class Snap {
        public String name = "";
        public long time;
        public int count;
        public long cheapest, median, resell, potential;
        public int discount, deals;
    }

    public static class SnapData {
        public Map<String, Snap> snaps = new LinkedHashMap<>();
    }

    public static SnapData data = new SnapData();
    public static Status status = Status.IDLE;
    public static String message = "Ready.";
    public static String currentName = "-";
    public static long nextAllowed = 0;

    private static final Gson GSON = new GsonBuilder().create();
    private static final Deque<String> queue = new ArrayDeque<>();
    private static final Map<String, Long> seen = new HashMap<>();
    private static int rot = 0;
    private static Config.Watch current = null;
    private static long stateStart = 0, screenOpenedAt = -1, actionAt = 0;
    private static final List<Cand> acc = new ArrayList<>();
    private static int pagesScanned, sortClicks;
    private static boolean sortedMode, sortGiveUp, awaiting;
    private static Action lastAction = Action.NEXT;
    private static String lastSnap = "";
    private static int waitIgnore = -1;

    // ------------------------------------------------------------ persistence of the flip list

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("market-scout-snapshots.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                SnapData d = GSON.fromJson(Files.readString(p), SnapData.class);
                if (d != null && d.snaps != null) data = d;
            }
        } catch (Exception e) {
            System.err.println("[MarketScout] Could not read snapshots: " + e);
        }
    }

    private static void saveSnaps() {
        try {
            Files.writeString(path(), GSON.toJson(data));
        } catch (Exception e) {
            System.err.println("[MarketScout] Could not save snapshots: " + e);
        }
    }

    public static void scanOne(String name) {
        if (!queue.contains(name)) queue.add(name);
    }

    public static void scanAll() {
        for (Config.Watch w : Config.INSTANCE.watch) if (w.enabled) scanOne(w.name);
    }

    public static int queued() { return queue.size(); }

    // ------------------------------------------------------------ main loop

    public static void tick(MinecraftClient mc) {
        Config c = Config.INSTANCE;
        long now = System.currentTimeMillis();

        if (mc.player == null || mc.getNetworkHandler() == null || (!c.scouting && queue.isEmpty() && status != Status.SCANNING)) {
            if (status != Status.SCANNING || mc.player == null) status = Status.IDLE;
            return;
        }

        switch (status) {
            case ERROR -> {
                if (now >= nextAllowed) status = Status.IDLE;
            }
            case IDLE -> {
                if (now < nextAllowed || mc.currentScreen != null) return;
                Config.Watch w = chooseWatch(c);
                if (w == null) {
                    message = "Nothing to scan - add items to the watchlist";
                    return;
                }
                start(mc, now, w);
            }
            case SCANNING -> {
                if (now - stateStart > c.timeoutMs) {
                    if (!acc.isEmpty()) {
                        finishScan(mc, now);
                        return;
                    }
                    fail(mc, screenOpenedAt < 0 ? "Auction GUI did not open in time" : "No listings loaded for " + currentName);
                    return;
                }
                if (mc.currentScreen instanceof HandledScreen<?> hs
                        && hs.getScreenHandler() instanceof GenericContainerScreenHandler h) {
                    if (screenOpenedAt < 0) {
                        screenOpenedAt = now;
                        message = "Auction GUI open, reading...";
                        return;
                    }
                    if (now - screenOpenedAt >= c.scanDelayMs) scan(mc, h, now);
                }
            }
        }
    }

    private static Config.Watch chooseWatch(Config c) {
        while (!queue.isEmpty()) {
            String name = queue.poll();
            for (Config.Watch w : c.watch) if (w.name.equals(name)) return w;
        }
        if (!c.scouting) return null;
        int n = c.watch.size();
        for (int k = 0; k < n; k++) {
            Config.Watch w = c.watch.get((rot + k) % n);
            if (w.enabled) {
                rot = (rot + k + 1) % n;
                return w;
            }
        }
        return null;
    }

    private static void start(MinecraftClient mc, long now, Config.Watch w) {
        Config c = Config.INSTANCE;
        c.sanitize();
        current = w;
        currentName = w.name;
        String term = w.search == null ? "" : w.search.replaceAll("[\\r\\n/]", " ").trim();
        nextAllowed = now + c.cooldownMs;
        if (term.isEmpty()) {
            fail(mc, w.name + " has no search term");
            return;
        }
        acc.clear();
        pagesScanned = 0;
        sortClicks = 0;
        sortedMode = false;
        sortGiveUp = false;
        awaiting = false;
        screenOpenedAt = -1;
        stateStart = now;
        status = Status.SCANNING;
        message = "Scanning " + w.name + " (/ah " + term + ")...";
        mc.getNetworkHandler().sendChatCommand("ah " + term);
    }

    // ------------------------------------------------------------ scanning

    private static String snapshot(GenericContainerScreenHandler h, int ignore) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < h.slots.size(); i++) {
            if (i == ignore) continue;
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
        screenOpenedAt = now;
        stateStart = now;
        mc.interactionManager.clickSlot(h.syncId, idx, 0, SlotActionType.PICKUP, mc.player);
    }

    private static void scan(MinecraftClient mc, GenericContainerScreenHandler h, long now) {
        Config c = Config.INSTANCE;
        Config.Watch w = current;
        String me = mc.player.getName().getString();

        if (awaiting) {
            String snap = snapshot(h, waitIgnore);
            if (snap.equals(lastSnap) && now - actionAt < 2500) return;
            boolean changed = !snap.equals(lastSnap);
            awaiting = false;
            if (!changed) {
                if (lastAction == Action.NEXT) { finishScan(mc, now); return; }
                sortGiveUp = true;
            }
        }

        List<Cand> page = new ArrayList<>();
        int stacks = 0, nextIdx = -1, sortIdx = -1;
        for (int i = 0; i < h.slots.size(); i++) {
            Slot s = h.slots.get(i);
            if (s.inventory instanceof PlayerInventory) continue;
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            stacks++;
            String nm = PriceParser.clean(st.getName().getString()).toLowerCase(Locale.ROOT);
            boolean isNext = nm.contains("next"), isSort = nm.contains("sort");
            PriceParser.Result r = PriceParser.analyze(st, mc.player);
            if (r == null) {
                // a button, not a listing (never click anything with a price)
                if (isNext && nextIdx < 0) nextIdx = i;
                else if (isSort && sortIdx < 0) sortIdx = i;
                continue;
            }
            if (!w.matches(st)) continue;
            if (PriceParser.mentionsPlayer(st, mc.player, me)) continue; // my own listing
            int cnt = Math.max(1, st.getCount());
            long unit = (c.perItem && !r.perUnit()) ? r.price() / cnt : r.price();
            if (unit > 0) page.add(new Cand(unit, r.price(), cnt));
        }

        if (stacks == 0) {
            if (pagesScanned == 0) { message = "Waiting for listings to load..."; return; }
            finishScan(mc, now);
            return;
        }

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
        pagesScanned++;

        if (wantNext) {
            message = "Reading page " + (pagesScanned + 1) + " of " + w.name + "...";
            clickGui(mc, h, nextIdx, Action.NEXT, now);
            return;
        }
        finishScan(mc, now);
    }

    private static void finishScan(MinecraftClient mc, long now) {
        Config c = Config.INSTANCE;
        Config.Watch w = current;
        awaiting = false;

        if (acc.isEmpty()) {
            closeGui(mc);
            fail(mc, "No priced listings found for " + w.name);
            return;
        }

        List<Cand> list = new ArrayList<>(acc);
        list.sort(Comparator.comparingLong(Cand::unit));
        List<Long> units = new ArrayList<>();
        for (Cand cd : list) units.add(cd.unit());
        Market.Stats s = Market.of(units);

        int deals = 0;
        for (long u : units) if (s.isDeal(u)) deals++;

        Snap sn = new Snap();
        sn.name = w.name;
        sn.time = now;
        sn.count = s.n();
        sn.cheapest = s.cheapest();
        sn.median = s.median();
        sn.resell = s.net();
        sn.deals = deals;
        sn.discount = s.discount(s.cheapest());
        sn.potential = s.isDeal(s.cheapest()) ? Math.max(0, s.potential(s.cheapest())) : 0;
        data.snaps.put(w.name, sn);
        saveSnaps();
        PriceLog.record(w.name, s.cheapest());

        // alerts
        int alerts = 0;
        for (Cand cd : list) {
            if (!s.isDeal(cd.unit()) || alerts >= 3) break;
            long potential = s.potential(cd.unit());
            if (potential < c.minDealProfit) continue;
            String key = w.name + ":" + cd.raw() + ":" + cd.count();
            Long last = seen.get(key);
            if (last != null && now - last < 600_000L) continue;
            seen.put(key, now);
            alerts++;
            int disc = s.discount(cd.unit());
            Deals.record(w.name, cd.unit(), s.net(), potential, disc);
            String msg = "DEAL: " + w.name + " " + Fmt.money(cd.unit()) + " (" + disc + "% under market, resell ~"
                    + Fmt.money(s.net()) + ", +" + Fmt.money(potential) + ")";
            Alerts.chat(mc, "\u00a7a" + msg);
            Alerts.fire(mc, "DEAL: " + w.name + " " + Fmt.money(cd.unit()),
                    disc + "% under  |  resell ~" + Fmt.money(s.net()) + "  |  +" + Fmt.money(potential), msg);
        }
        if (w.alertBelow > 0 && s.cheapest() <= w.alertBelow) {
            String key = w.name + ":below:" + s.cheapest();
            Long last = seen.get(key);
            if (last == null || now - last > 600_000L) {
                seen.put(key, now);
                String msg = w.name + " is " + Fmt.money(s.cheapest()) + " (your alert: " + Fmt.money(w.alertBelow) + ")";
                Alerts.chat(mc, "\u00a7b" + msg);
                Alerts.fire(mc, w.name + " hit your price", msg, msg);
            }
        }

        status = Status.IDLE;
        message = w.name + ": cheapest " + Fmt.money(s.cheapest()) + ", median " + Fmt.money(s.median())
                + ", " + s.n() + " listings, " + deals + " deal(s)";
        closeGui(mc);
    }

    private static void closeGui(MinecraftClient mc) {
        if (mc.player != null && mc.currentScreen instanceof HandledScreen<?>) mc.player.closeHandledScreen();
    }

    private static void fail(MinecraftClient mc, String msg) {
        status = Status.ERROR;
        message = msg;
        nextAllowed = Math.max(nextAllowed, System.currentTimeMillis() + Config.INSTANCE.cooldownMs);
        closeGui(mc);
    }
}
