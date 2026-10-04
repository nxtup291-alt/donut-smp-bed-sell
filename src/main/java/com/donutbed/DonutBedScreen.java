package com.donutbed;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.BedItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class DonutBedScreen extends Screen {
    private static final int W = 360;
    private static final int H = 252;
    private static final int SIDE = 80;
    private static final int ROWS = 12;
    private static final int ACCENT = 0xFF4C8DFF;

    // tabs: 0 dashboard, 1 settings, 2 items, 3 prices, 4 alerts, 5 profit, 6 history
    private static final String[] TABS = { "Dashboard", "Settings", "Items", "Prices", "Alerts", "Profit", "History" };
    private static final String[] RANGES = { "1H", "1D", "7D", "All" };
    private static final long[] RANGE_MS = { 3_600_000L, 86_400_000L, 7L * 86_400_000L, 0L };

    private static int tab = 0;
    private static int page = 0;
    private static int itemSel = 0;
    private static int priceSel = 0;
    private static int priceRange = 1;
    private static String note = "";
    private static long noteUntil = 0;
    private static final Map<String, Float> ANIM = new HashMap<>();

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private final List<TextFieldWidget> fields = new ArrayList<>();
    private boolean resetArmed = false;
    private int left, top, cx, cw;

    public DonutBedScreen() {
        super(Text.literal("Donut Bed Client"));
    }

    private void reopen() {
        if (client != null) client.setScreen(new DonutBedScreen());
    }

    private static Config.ItemCfg selItem() {
        List<Config.ItemCfg> items = Config.INSTANCE.items;
        itemSel = Math.max(0, Math.min(itemSel, items.size() - 1));
        return items.get(itemSel);
    }

    // ------------------------------------------------------------ setup

    @Override
    protected void init() {
        Config c = Config.INSTANCE;
        fields.clear();
        left = (width - W) / 2;
        top = Math.max(8, (height - H) / 2);
        cx = left + SIDE + 12;
        cw = W - SIDE - 22;
        int fx = cx + cw - 86;
        int y0 = top + 36;

        if (tab == 1) {
            int base = y0 + 16 + 3 * 21 + 4;
            numField(fx, base + 1, 80, String.valueOf(c.undercut), s -> c.undercut = num(s, c.undercut));
            numField(fx, base + 21, 80, String.valueOf(c.minPrice), s -> c.minPrice = num(s, c.minPrice));
            numField(fx, base + 41, 80, String.valueOf(c.outlierPercent), s -> c.outlierPercent = (int) Math.min(num(s, c.outlierPercent), 1000));
            numField(fx, base + 61, 80, String.valueOf(c.scanDelayMs), s -> c.scanDelayMs = (int) Math.min(num(s, c.scanDelayMs), 1_000_000));
            numField(fx, base + 81, 80, String.valueOf(c.timeoutMs / 1000), s -> c.timeoutMs = (int) Math.min(num(s, c.timeoutMs / 1000) * 1000, 1_000_000));
            numField(fx, base + 101, 80, String.valueOf(c.cooldownMs / 1000), s -> c.cooldownMs = (int) Math.min(num(s, c.cooldownMs / 1000) * 1000, 1_000_000));
        } else if (tab == 2) {
            Config.ItemCfg it = selItem();
            int base = y0 + 84;
            textField(cx + cw - 126, base + 1, 120, 40, it.search, s -> it.search = s);
            numField(fx, base + 22, 80, String.valueOf(it.cost), s -> it.cost = s.isEmpty() ? 0 : num(s, it.cost));
            numField(fx, base + 43, 80, it.minPrice < 0 ? "" : String.valueOf(it.minPrice), s -> it.minPrice = s.isEmpty() ? -1 : num(s, it.minPrice));
            numField(fx, base + 64, 80, it.undercut < 0 ? "" : String.valueOf(it.undercut), s -> it.undercut = s.isEmpty() ? -1 : num(s, it.undercut));
        } else if (tab == 4) {
            textField(cx, y0 + 100, cw, 200, c.discordWebhook, s -> c.discordWebhook = s.trim());
        }
    }

    private static long num(String s, long old) {
        if (s == null || s.isEmpty()) return old;
        try { return Long.parseLong(s); } catch (Exception e) { return old; }
    }

    private void numField(int x, int y, int w, String value, Consumer<String> onChange) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 18, Text.empty());
        f.setMaxLength(15);
        f.setTextPredicate(s -> s.matches("\\d*"));
        f.setText(value);
        f.setChangedListener(onChange);
        addDrawableChild(f);
        fields.add(f);
    }

    private void textField(int x, int y, int w, int max, String value, Consumer<String> onChange) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 18, Text.empty());
        f.setMaxLength(max);
        f.setText(value == null ? "" : value);
        f.setChangedListener(onChange);
        addDrawableChild(f);
        fields.add(f);
    }

    @Override
    public void removed() {
        Config.INSTANCE.sanitize();
        Config.INSTANCE.save();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean typing = fields.stream().anyMatch(TextFieldWidget::isFocused);
        if (!typing && DonutBedClient.openKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (Hit h : new ArrayList<>(hits)) {
                if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
                    if (client != null) {
                        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    h.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    // ------------------------------------------------------------ drawing helpers

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int lerp(int a, int b, float t) {
        int r = 0;
        for (int s = 24; s >= 0; s -= 8) {
            int ca = (a >> s) & 255, cb = (b >> s) & 255;
            r |= (Math.round(ca + (cb - ca) * t) & 255) << s;
        }
        return r;
    }

    private static int shade(int c, int d) {
        int r = 0xFF000000;
        for (int s = 16; s >= 0; s -= 8) {
            int ch = Math.max(0, Math.min(255, ((c >> s) & 255) + d));
            r |= ch << s;
        }
        return r;
    }

    /** Rectangle with softly cut corners. Opaque colors only. */
    private static void rrect(DrawContext g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + 1, color);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, color);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
    }

    private void txt(DrawContext g, String s, int x, int y, int color) {
        g.drawTextWithShadow(textRenderer, Text.literal(s), x, y, color);
    }

    private void txtRight(DrawContext g, String s, int rightX, int y, int color) {
        txt(g, s, rightX - textRenderer.getWidth(s), y, color);
    }

    private void big(DrawContext g, String s, int x, int y, float sc, int color) {
        var m = g.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(sc, sc, 1f);
        g.drawTextWithShadow(textRenderer, Text.literal(s), 0, 0, color);
        m.pop();
    }

    private void button(DrawContext g, int mx, int my, int x, int y, int w, int h,
                        String label, int top1, int bottom1, Runnable action) {
        boolean hov = in(mx, my, x, y, w, h);
        int d = hov ? 28 : 0;
        rrect(g, x, y, w, h, shade(bottom1, -40 + d));
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, shade(top1, d), shade(bottom1, d));
        g.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        hits.add(new Hit(x, y, w, h, action));
    }

    private void smallButton(DrawContext g, int mx, int my, int x, int y, int w, int h, String label, boolean active, Runnable action) {
        button(g, mx, my, x, y, w, h, label, active ? 0xFF4C8DFF : 0xFF3A3F5C, active ? 0xFF2A56B8 : 0xFF262A44, action);
    }

    private void toggleRow(DrawContext g, int mx, int my, int x, int y, int w,
                           String label, boolean val, Runnable click) {
        boolean hov = in(mx, my, x, y, w, 20);
        rrect(g, x, y, w, 20, hov ? 0xFF22264A : 0xFF1A1D33);
        txt(g, label, x + 8, y + 6, 0xFFE6E8FF);
        float cur = ANIM.getOrDefault(label, val ? 1f : 0f);
        cur += ((val ? 1f : 0f) - cur) * 0.25f;
        ANIM.put(label, cur);
        int tx = x + w - 36, ty = y + 4;
        rrect(g, tx, ty, 28, 12, lerp(0xFF3A3F5C, 0xFF2FBF71, cur));
        rrect(g, tx + 2 + Math.round(cur * 16), ty + 2, 8, 8, 0xFFFFFFFF);
        hits.add(new Hit(x, y, w, 20, click));
    }

    private void card(DrawContext g, int x, int y, int w, int h, String label, String value, int vcolor) {
        rrect(g, x, y, w, h, 0xFF1A1D33);
        g.fill(x + 1, y + 3, x + 3, y + h - 3, vcolor);
        txt(g, label, x + 8, y + 6, 0xFF8A90B8);
        txt(g, textRenderer.trimToWidth(value, w - 12), x + 8, y + h - 14, vcolor);
    }

    private void labelRow(DrawContext g, int y, String label) {
        rrect(g, cx, y, cw, 20, 0xFF1A1D33);
        txt(g, label, cx + 8, y + 6, 0xFFE6E8FF);
    }

    private static String money(long v) {
        return v < 0 ? "-" : Profit.compact(v);
    }

    private static int profitColor(long v) {
        return v >= 0 ? 0xFF4DE08A : 0xFFFF5D62;
    }

    private static int statusColor(AutoSeller.Status s) {
        return switch (s) {
            case IDLE -> 0xFF9AA0C0;
            case SCANNING -> 0xFFFFD84D;
            case PRICE_FOUND -> 0xFF4DE08A;
            case SELLING -> 0xFF4DD8FF;
            case ERROR -> 0xFFFF5D62;
        };
    }

    private static int pulsing(AutoSeller.Status s) {
        int base = statusColor(s);
        if (s == AutoSeller.Status.SCANNING || s == AutoSeller.Status.SELLING) {
            float p = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 220.0);
            return lerp(base, 0xFF202438, p * 0.7f);
        }
        return base;
    }

    // ------------------------------------------------------------ rendering

    @Override
    public void renderBackground(DrawContext g, int mx, int my, float delta) {
        super.renderBackground(g, mx, my, delta);
        for (int i = 1; i <= 5; i++) {
            g.fill(left - i, top - i, left + W + i, top + H + i, 0x0C000000);
        }
        rrect(g, left - 1, top - 1, W + 2, H + 2, 0xFF2B2F52);
        g.fillGradient(left, top, left + W, top + H, 0xFA171A2E, 0xFA0B0C16);
    }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        hits.clear();
        super.render(g, mx, my, delta);
        drawChrome(g, mx, my);
        switch (tab) {
            case 0 -> renderDashboard(g, mx, my);
            case 1 -> renderSettings(g, mx, my);
            case 2 -> renderItems(g, mx, my);
            case 3 -> renderPrices(g, mx, my);
            case 4 -> renderAlerts(g, mx, my);
            case 5 -> renderProfit(g, mx, my);
            default -> renderHistory(g, mx, my);
        }
    }

    private void drawChrome(DrawContext g, int mx, int my) {
        g.fillGradient(left, top, left + W, top + 28, 0xFF2E58B8, 0xFF182244);
        big(g, "DONUT BED CLIENT", left + 10, top + 9, 1.25f, 0xFFFFFFFF);
        txtRight(g, "auto-undercut seller", left + W - 8, top + 10, 0xFFB7C6F5);
        g.fill(left, top + 28, left + W, top + 30, 0xFF23274A);
        int seg = left + (int) ((System.currentTimeMillis() / 6) % (W + 80)) - 80;
        g.fill(Math.max(left, seg), top + 28, Math.min(left + W, seg + 80), top + 30, ACCENT);

        g.fill(left, top + 30, left + SIDE, top + H, 0xFF0D0E1A);
        g.fill(left + SIDE, top + 30, left + SIDE + 1, top + H, 0xFF23274A);
        for (int i = 0; i < TABS.length; i++) {
            int x = left + 4, y = top + 36 + i * 23, w = SIDE - 8;
            boolean sel = tab == i;
            boolean hov = in(mx, my, x, y, w, 21);
            if (sel) rrect(g, x, y, w, 21, 0xFF1D2347);
            else if (hov) rrect(g, x, y, w, 21, 0xFF151833);
            if (sel) g.fill(x, y + 3, x + 2, y + 18, ACCENT);
            txt(g, TABS[i], x + 10, y + 6, sel ? 0xFFFFFFFF : 0xFF8A90B8);
            final int idx = i;
            hits.add(new Hit(x, y, w, 21, () -> {
                tab = idx;
                page = 0;
                reopen();
            }));
        }

        AutoSeller.Status st = AutoSeller.status;
        int fy = top + H - 38;
        g.fill(left + 8, fy - 6, left + SIDE - 8, fy - 5, 0xFF23274A);
        rrect(g, left + 8, fy + 2, 6, 6, pulsing(st));
        txt(g, st.name().replace('_', ' '), left + 18, fy, statusColor(st));
        txt(g, "Total", left + 8, fy + 13, 0xFF6C7298);
        long total = Profit.total();
        txt(g, textRenderer.trimToWidth(Profit.compactSigned(total), SIDE - 14), left + 8, fy + 23, profitColor(total));
    }

    private void title(DrawContext g, String s, int y) {
        big(g, s, cx, y, 1.3f, 0xFFFFFFFF);
    }

    // ---- dashboard

    private void renderDashboard(DrawContext g, int mx, int my) {
        Config c = Config.INSTANCE;
        AutoSeller.Status st = AutoSeller.status;
        int y0 = top + 36;
        title(g, "Dashboard", y0);

        int y = y0 + 18;
        rrect(g, cx, y, cw, 46, 0xFF1A1D33);
        g.fill(cx + 1, y + 3, cx + 4, y + 43, statusColor(st));
        rrect(g, cx + 12, y + 9, 8, 8, pulsing(st));
        big(g, st.name().replace('_', ' '), cx + 26, y + 8, 1.4f, statusColor(st));
        txtRight(g, "Item: " + AutoSeller.currentName, cx + cw - 8, y + 10, 0xFF8A90B8);
        txt(g, textRenderer.trimToWidth(AutoSeller.message, cw - 20), cx + 12, y + 30,
                st == AutoSeller.Status.ERROR ? 0xFFFF8D90 : 0xFFA6ABCB);

        y += 54;
        int cwid = (cw - 8) / 3;
        card(g, cx, y, cwid, 36, "MARKET PRICE", money(AutoSeller.cheapest), 0xFFFFFFFF);
        card(g, cx + cwid + 4, y, cwid, 36, "SELLING AT", money(AutoSeller.sellPrice), 0xFF4DD8FF);
        long total = Profit.total();
        card(g, cx + 2 * (cwid + 4), y, cwid, 36, "PROFIT", Profit.compactSigned(total), profitColor(total));

        y += 44;
        boolean on = c.enabled;
        button(g, mx, my, cx, y, cw, 28, on ? "STOP" : "START",
                on ? 0xFFEF5358 : 0xFF38CC7B, on ? 0xFF9E2B30 : 0xFF1C8A50, () -> {
                    c.enabled = !c.enabled;
                    c.save();
                    if (c.enabled) close();
                });

        y += 36;
        int enabledCount = 0;
        for (Config.ItemCfg it : c.items) if (it.enabled) enabledCount++;
        txt(g, "Auto Sell: " + (c.autoSell ? "ON" : "OFF") + "   Auto-pick: " + (c.autoPickBed ? "ON" : "OFF")
                + "   Outlier guard: " + (c.outlierProtect ? "ON" : "OFF"), cx, y, 0xFF8A90B8);
        txt(g, enabledCount + " item(s) rotating   Undercut " + Profit.money(c.undercut) + "   Min " + Profit.money(c.minPrice),
                cx, y + 12, 0xFF8A90B8);
        txt(g, "Menu key: " + DonutBedClient.openKey.getBoundKeyLocalizedText().getString(), cx, y + 24, 0xFF6C7298);
    }

    // ---- settings

    private void renderSettings(DrawContext g, int mx, int my) {
        Config c = Config.INSTANCE;
        int y0 = top + 36;
        title(g, "Settings", y0);

        int y = y0 + 16;
        toggleRow(g, mx, my, cx, y, cw, "Auto Sell", c.autoSell, () -> { c.autoSell = !c.autoSell; c.save(); });
        toggleRow(g, mx, my, cx, y + 21, cw, "Auto-pick item from inventory", c.autoPickBed, () -> { c.autoPickBed = !c.autoPickBed; c.save(); });
        toggleRow(g, mx, my, cx, y + 42, cw, "Outlier protection", c.outlierProtect, () -> { c.outlierProtect = !c.outlierProtect; c.save(); });

        String[] labels = { "Default undercut ($)", "Default minimum price ($)", "Lowball threshold (%)",
                "Scan delay (ms)", "Auction timeout (s)", "Cooldown (s)" };
        int base = y + 3 * 21 + 4;
        for (int i = 0; i < labels.length; i++) {
            rrect(g, cx, base + i * 20, cw, 19, 0xFF1A1D33);
            txt(g, labels[i], cx + 8, base + i * 20 + 6, 0xFFE6E8FF);
        }
    }

    // ---- items

    private void renderItems(DrawContext g, int mx, int my) {
        Config c = Config.INSTANCE;
        int y0 = top + 36;
        title(g, "Items", y0);
        List<Config.ItemCfg> items = c.items;
        int n = items.size();
        Config.ItemCfg it = selItem();

        // selector
        int sy = y0 + 16;
        smallButton(g, mx, my, cx, sy, 24, 20, "<", true, () -> { itemSel = (itemSel - 1 + n) % n; reopen(); });
        smallButton(g, mx, my, cx + cw - 24, sy, 24, 20, ">", true, () -> { itemSel = (itemSel + 1) % n; reopen(); });
        rrect(g, cx + 28, sy, cw - 56, 20, 0xFF1A1D33);
        g.drawCenteredTextWithShadow(textRenderer,
                Text.literal(textRenderer.trimToWidth(it.name, cw - 100) + "  (" + (itemSel + 1) + "/" + n + ")"),
                cx + cw / 2, sy + 6, 0xFFFFFFFF);

        toggleRow(g, mx, my, cx, y0 + 40, cw, "Enabled (rotates in)", it.enabled, () -> { it.enabled = !it.enabled; c.save(); });

        String m = "@bed".equals(it.match) ? "Any bed (all colors)" : it.match;
        rrect(g, cx, y0 + 62, cw, 19, 0xFF12142A);
        txt(g, textRenderer.trimToWidth("Matches: " + m, cw - 12), cx + 8, y0 + 67, 0xFF8A90B8);

        int base = y0 + 84;
        String[] labels = { "Search term (/ah ...)", "Cost per item ($)", "Minimum price ($)", "Undercut ($)" };
        for (int i = 0; i < labels.length; i++) {
            rrect(g, cx, base + i * 21, cw, 20, 0xFF1A1D33);
            txt(g, labels[i], cx + 8, base + i * 21 + 6, 0xFFE6E8FF);
        }
        // "global" hints when empty
        int fx = cx + cw - 86;
        if (it.minPrice < 0) txt(g, "global", fx + 6, base + 2 * 21 + 6, 0xFF5A6088);
        if (it.undercut < 0) txt(g, "global", fx + 6, base + 3 * 21 + 6, 0xFF5A6088);

        int by = y0 + 172;
        smallButton(g, mx, my, cx, by, 140, 18, "+ Add item in hand", true, () -> addHeld(c));
        button(g, mx, my, cx + cw - 100, by, 100, 18, "Remove item",
                n > 1 ? 0xFFEF5358 : 0xFF3A3F5C, n > 1 ? 0xFF9E2B30 : 0xFF262A44, () -> {
                    if (n > 1) {
                        items.remove(itemSel);
                        itemSel = 0;
                        c.save();
                        reopen();
                    }
                });

        boolean showNote = System.currentTimeMillis() < noteUntil;
        txt(g, showNote ? note : "Items rotate each round. Empty = use the Settings default.", cx, y0 + 194,
                showNote ? 0xFFFFD84D : 0xFF6C7298);
    }

    private void addHeld(Config c) {
        if (client == null || client.player == null) return;
        ItemStack held = client.player.getMainHandStack();
        if (held.isEmpty()) {
            note = "Hold the item in your main hand first (close menu, hold it, reopen).";
            noteUntil = System.currentTimeMillis() + 4000;
            return;
        }
        Config.ItemCfg n = new Config.ItemCfg();
        if (held.getItem() instanceof BedItem) {
            n.name = "Bed";
            n.match = "@bed";
            n.search = "bed";
        } else {
            Identifier id = Registries.ITEM.getId(held.getItem());
            n.match = id.toString();
            n.name = held.getItem().getName().getString();
            n.search = id.getPath().replace('_', ' ');
        }
        for (int i = 0; i < c.items.size(); i++) {
            if (c.items.get(i).match.equals(n.match)) {
                itemSel = i;
                note = n.name + " is already in the list.";
                noteUntil = System.currentTimeMillis() + 3000;
                reopen();
                return;
            }
        }
        n.cost = 0;
        c.items.add(n);
        itemSel = c.items.size() - 1;
        c.save();
        note = "Added " + n.name + ". Set its cost and search term below.";
        noteUntil = System.currentTimeMillis() + 5000;
        reopen();
    }

    // ---- prices

    private void renderPrices(DrawContext g, int mx, int my) {
        Config c = Config.INSTANCE;
        int y0 = top + 36;
        title(g, "Prices", y0);
        List<Config.ItemCfg> items = c.items;
        int n = items.size();
        priceSel = Math.max(0, Math.min(priceSel, n - 1));
        String name = items.get(priceSel).name;

        int sy = y0 + 16;
        smallButton(g, mx, my, cx, sy, 22, 20, "<", true, () -> { priceSel = (priceSel - 1 + n) % n; });
        smallButton(g, mx, my, cx + 120, sy, 22, 20, ">", true, () -> { priceSel = (priceSel + 1) % n; });
        rrect(g, cx + 24, sy, 94, 20, 0xFF1A1D33);
        g.drawCenteredTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(name, 88)), cx + 71, sy + 6, 0xFFFFFFFF);
        int rx = cx + 150;
        int rw = (cw - 150 - 6) / 4;
        for (int i = 0; i < RANGES.length; i++) {
            final int idx = i;
            smallButton(g, mx, my, rx + i * (rw + 2), sy, rw, 20, RANGES[i], priceRange == i, () -> priceRange = idx);
        }

        long since = RANGE_MS[priceRange] == 0 ? 0 : System.currentTimeMillis() - RANGE_MS[priceRange];
        List<PriceLog.Point> pts = PriceLog.points(name, since);

        long now = -1, lo = -1, hi = -1, avg = -1;
        if (!pts.isEmpty()) {
            now = pts.get(pts.size() - 1).p;
            lo = Long.MAX_VALUE;
            hi = Long.MIN_VALUE;
            long sum = 0;
            for (PriceLog.Point p : pts) {
                lo = Math.min(lo, p.p);
                hi = Math.max(hi, p.p);
                sum += p.p;
            }
            avg = sum / pts.size();
        }
        int cwid = (cw - 12) / 4;
        card(g, cx, y0 + 42, cwid, 34, "NOW", money(now), 0xFFFFFFFF);
        card(g, cx + (cwid + 4), y0 + 42, cwid, 34, "LOW", money(lo), 0xFF4DE08A);
        card(g, cx + 2 * (cwid + 4), y0 + 42, cwid, 34, "HIGH", money(hi), 0xFFFF5D62);
        card(g, cx + 3 * (cwid + 4), y0 + 42, cwid, 34, "AVERAGE", money(avg), 0xFF4DD8FF);

        drawChart(g, mx, my, cx, y0 + 82, cw, 104, pts);
        if (!pts.isEmpty()) {
            txt(g, PriceLog.shortDate(pts.get(0).t), cx, y0 + 190, 0xFF6C7298);
            txtRight(g, PriceLog.shortDate(pts.get(pts.size() - 1).t), cx + cw, y0 + 190, 0xFF6C7298);
        }
    }

    private static double interp(List<PriceLog.Point> pts, int j, long t) {
        PriceLog.Point a = pts.get(j);
        PriceLog.Point b = pts.get(Math.min(j + 1, pts.size() - 1));
        if (b.t == a.t) return a.p;
        double f = Math.max(0.0, Math.min(1.0, (t - a.t) / (double) (b.t - a.t)));
        return a.p + (b.p - a.p) * f;
    }

    private void drawChart(DrawContext g, int mx, int my, int x, int y, int w, int h, List<PriceLog.Point> pts) {
        rrect(g, x, y, w, h, 0xFF12142A);
        if (pts.isEmpty()) {
            txt(g, "No price data yet. Run the mod to collect prices.", x + 8, y + h / 2 - 4, 0xFF6C7298);
            return;
        }
        long tmin = pts.get(0).t, tmax = pts.get(pts.size() - 1).t;
        if (tmax == tmin) tmax = tmin + 1;
        long vmin = Long.MAX_VALUE, vmax = Long.MIN_VALUE;
        for (PriceLog.Point p : pts) {
            vmin = Math.min(vmin, p.p);
            vmax = Math.max(vmax, p.p);
        }
        long realMin = vmin, realMax = vmax;
        if (vmax == vmin) { vmax++; vmin--; }

        int ix = x + 6, iw = w - 12, ytop = y + 16, bot = y + h - 8, ih = bot - ytop;
        for (int i = 0; i <= 3; i++) {
            int gy = ytop + ih * i / 3;
            g.fill(ix, gy, ix + iw, gy + 1, 0xFF1E2140);
        }

        int j = 0, prevPy = -1;
        for (int col = 0; col < iw; col++) {
            long t = tmin + (tmax - tmin) * col / Math.max(1, iw - 1);
            while (j < pts.size() - 2 && pts.get(j + 1).t <= t) j++;
            double v = pts.size() == 1 ? pts.get(0).p : interp(pts, j, t);
            int py = bot - (int) Math.round((v - vmin) / (double) (vmax - vmin) * ih);
            g.fill(ix + col, py, ix + col + 1, bot, 0x224C8DFF);
            int a = prevPy < 0 ? py : Math.min(py, prevPy);
            int b = (prevPy < 0 ? py : Math.max(py, prevPy)) + 2;
            g.fill(ix + col, a, ix + col + 1, b, 0xFF6EA8FF);
            prevPy = py;
        }

        txt(g, Profit.compact(realMax), ix + 2, y + 4, 0xFF8A90B8);
        txt(g, Profit.compact(realMin), ix + 2, bot - 10, 0xFF8A90B8);

        // hover readout
        if (in(mx, my, ix, y, iw, h)) {
            int col = mx - ix;
            long t = tmin + (tmax - tmin) * col / Math.max(1, iw - 1);
            int k = 0;
            while (k < pts.size() - 2 && pts.get(k + 1).t <= t) k++;
            double v = pts.size() == 1 ? pts.get(0).p : interp(pts, k, t);
            g.fill(mx, ytop, mx + 1, bot, 0x88FFFFFF);
            txtRight(g, PriceLog.shortDate(t) + "  " + Profit.compact(Math.round(v)), x + w - 6, y + 4, 0xFFFFFFFF);
        } else {
            txtRight(g, pts.size() + " points", x + w - 6, y + 4, 0xFF6C7298);
        }
    }

    // ---- alerts

    private void renderAlerts(DrawContext g, int mx, int my) {
        Config c = Config.INSTANCE;
        int y0 = top + 36;
        title(g, "Alerts", y0);
        int y = y0 + 16;
        toggleRow(g, mx, my, cx, y, cw, "Chat notifications", c.notifications, () -> { c.notifications = !c.notifications; c.save(); });
        toggleRow(g, mx, my, cx, y + 22, cw, "Sound alert", c.soundAlert, () -> { c.soundAlert = !c.soundAlert; c.save(); });
        toggleRow(g, mx, my, cx, y + 44, cw, "Popup toast", c.toastAlert, () -> { c.toastAlert = !c.toastAlert; c.save(); });

        txt(g, "Discord webhook URL (optional)", cx, y0 + 88, 0xFFE6E8FF);
        if (c.discordWebhook == null || c.discordWebhook.isEmpty()) {
            txt(g, "https://discord.com/api/webhooks/...", cx + 5, y0 + 105, 0xFF4A5078);
        }
        button(g, mx, my, cx, y0 + 126, cw, 22, "Send test alert", 0xFF4C8DFF, 0xFF2A56B8, () -> {
            c.save();
            AutoSeller.testAlert(client);
        });
        txt(g, "Alerts fire when an item is listed for sale.", cx, y0 + 156, 0xFF6C7298);
        txt(g, "The webhook sends the same message to your Discord channel.", cx, y0 + 168, 0xFF6C7298);
    }

    // ---- profit

    private void renderProfit(DrawContext g, int mx, int my) {
        int y0 = top + 36;
        title(g, "Profit", y0);

        rrect(g, cx, y0 + 16, cw, 20, 0xFF12142A);
        txt(g, "Cost per item is set in the Items tab.", cx + 8, y0 + 22, 0xFF8A90B8);

        long total = Profit.total();
        txt(g, "TOTAL PROFIT", cx, y0 + 42, 0xFF8A90B8);
        big(g, Profit.signed(total), cx, y0 + 52, 2f, profitColor(total));

        int y = y0 + 76;
        int cwid = (cw - 8) / 3;
        long today = Profit.since(Profit.startOfToday());
        long week = Profit.since(System.currentTimeMillis() - 7L * 24 * 3600 * 1000);
        card(g, cx, y, cwid, 34, "TODAY", Profit.compactSigned(today), profitColor(today));
        card(g, cx + cwid + 4, y, cwid, 34, "7 DAYS", Profit.compactSigned(week), profitColor(week));
        card(g, cx + 2 * (cwid + 4), y, cwid, 34, "AVG / SALE", Profit.compactSigned(Profit.average()), profitColor(Profit.average()));

        txt(g, "Items listed: " + Profit.count() + "     Best: " + Profit.compactSigned(Profit.best()), cx, y + 40, 0xFF8A90B8);

        int chartY = y + 54, chartH = 44;
        rrect(g, cx, chartY, cw, chartH, 0xFF12142A);
        long[] daily = Profit.daily(7);
        long lo = 0, hi = 0;
        for (long v : daily) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        if (hi == lo) hi = lo + 1;
        float scale = (chartH - 8) / (float) (hi - lo);
        int baseY = chartY + 4 + Math.round(hi * scale);
        int slot = (cw - 8) / 7;
        for (int i = 0; i < 7; i++) {
            int bx = cx + 4 + i * slot + 4;
            int bw = slot - 8;
            int hgt = Math.round(Math.abs(daily[i]) * scale);
            if (daily[i] >= 0) g.fillGradient(bx, baseY - hgt, bx + bw, baseY, 0xFF4DE08A, 0xFF1C8A50);
            else g.fillGradient(bx, baseY, bx + bw, baseY + hgt, 0xFFFF5D62, 0xFF9E2B30);
            String lbl = Profit.dayLabel(6 - i);
            txt(g, lbl, bx + (bw - textRenderer.getWidth(lbl)) / 2, chartY + chartH + 3, i == 6 ? 0xFFFFFFFF : 0xFF6C7298);
        }
        g.fill(cx + 2, baseY, cx + cw - 2, baseY + 1, 0xFF3A3F5C);

        button(g, mx, my, cx + cw - 110, top + H - 24, 110, 16,
                resetArmed ? "Click to CONFIRM" : "Reset all data",
                resetArmed ? 0xFFEF5358 : 0xFF3A3F5C, resetArmed ? 0xFF9E2B30 : 0xFF262A44, () -> {
                    if (!resetArmed) {
                        resetArmed = true;
                    } else {
                        Profit.clear();
                        resetArmed = false;
                    }
                });
    }

    // ---- history

    private void renderHistory(DrawContext g, int mx, int my) {
        int y0 = top + 36;
        title(g, "History", y0);
        txtRight(g, Profit.count() + " sales", cx + cw, y0 + 4, 0xFF8A90B8);

        txt(g, "DATE", cx + 4, y0 + 20, ACCENT);
        txt(g, "ITEM", cx + 108, y0 + 20, ACCENT);
        txt(g, "LISTED AT", cx + 158, y0 + 20, ACCENT);
        txtRight(g, "PROFIT", cx + cw - 4, y0 + 20, ACCENT);
        g.fill(cx, y0 + 31, cx + cw, y0 + 32, 0xFF2B2F52);

        List<Profit.Entry> list = Profit.entries();
        if (list.isEmpty()) {
            txt(g, "No sales yet. They will show up here.", cx + 4, y0 + 42, 0xFF8A90B8);
        }
        for (int i = 0; i < ROWS; i++) {
            int idx = list.size() - 1 - (page * ROWS + i); // newest first
            if (idx < 0) break;
            Profit.Entry e = list.get(idx);
            int y = y0 + 34 + i * 13;
            if (i % 2 == 0) g.fill(cx, y - 1, cx + cw, y + 11, 0xFF161930);
            txt(g, Profit.date(e.time), cx + 4, y + 1, 0xFFDDE0F5);
            txt(g, textRenderer.trimToWidth(e.item == null ? "Bed" : e.item, 46), cx + 108, y + 1, 0xFFB7C6F5);
            txt(g, Profit.compact(e.price), cx + 158, y + 1, 0xFFFFFFFF);
            txtRight(g, Profit.compactSigned(e.profit), cx + cw - 4, y + 1, profitColor(e.profit));
        }

        int pages = Math.max(1, (list.size() + ROWS - 1) / ROWS);
        page = Math.min(page, pages - 1);
        int by = top + H - 24;
        smallButton(g, mx, my, cx, by, 60, 16, "< Newer", page > 0, () -> { if (page > 0) page--; });
        smallButton(g, mx, my, cx + cw - 60, by, 60, 16, "Older >", page < pages - 1, () -> { if (page < pages - 1) page++; });
        g.drawCenteredTextWithShadow(textRenderer, Text.literal("Page " + (page + 1) + "/" + pages),
                cx + cw / 2, by + 4, 0xFF8A90B8);
    }
}
