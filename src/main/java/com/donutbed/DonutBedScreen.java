package com.donutbed;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class DonutBedScreen extends Screen {
    private static final int W = 340;
    private static final int H = 252;
    private static final int SIDE = 80;
    private static final int ROWS = 12;
    private static final int ACCENT = 0xFF4C8DFF;

    private static int tab = 0; // 0 dashboard, 1 settings, 2 profit, 3 history
    private static int page = 0;
    private static final Map<String, Float> ANIM = new HashMap<>();
    private static final String[] TABS = { "Dashboard", "Settings", "Profit", "History" };

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
            int base = y0 + 16 + 3 * 22 + 6;
            field(fx, base + 1, String.valueOf(c.undercut), s -> c.undercut = num(s, c.undercut));
            field(fx, base + 22, String.valueOf(c.minPrice), s -> c.minPrice = num(s, c.minPrice));
            field(fx, base + 43, String.valueOf(c.scanDelayMs), s -> c.scanDelayMs = (int) Math.min(num(s, c.scanDelayMs), 1_000_000));
            field(fx, base + 64, String.valueOf(c.timeoutMs / 1000), s -> c.timeoutMs = (int) Math.min(num(s, c.timeoutMs / 1000) * 1000, 1_000_000));
            field(fx, base + 85, String.valueOf(c.cooldownMs / 1000), s -> c.cooldownMs = (int) Math.min(num(s, c.cooldownMs / 1000) * 1000, 1_000_000));
        } else if (tab == 2) {
            field(fx, y0 + 17, String.valueOf(c.bedCost), s -> c.bedCost = num(s, c.bedCost));
        }
    }

    private static long num(String s, long old) {
        if (s == null || s.isEmpty()) return old;
        try { return Long.parseLong(s); } catch (Exception e) { return old; }
    }

    private void field(int x, int y, String value, Consumer<String> onChange) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, 80, 18, Text.empty());
        f.setMaxLength(15);
        f.setTextPredicate(s -> s.matches("\\d*"));
        f.setText(value);
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

    private static String money(long v) {
        return v < 0 ? "-" : Profit.money(v);
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
            case 2 -> renderProfit(g, mx, my);
            default -> renderHistory(g, mx, my);
        }
    }

    private void drawChrome(DrawContext g, int mx, int my) {
        // header
        g.fillGradient(left, top, left + W, top + 28, 0xFF2E58B8, 0xFF182244);
        big(g, "DONUT BED CLIENT", left + 10, top + 9, 1.25f, 0xFFFFFFFF);
        txtRight(g, "auto-undercut seller", left + W - 8, top + 10, 0xFFB7C6F5);
        g.fill(left, top + 28, left + W, top + 30, 0xFF23274A);
        int seg = left + (int) ((System.currentTimeMillis() / 6) % (W + 80)) - 80;
        g.fill(Math.max(left, seg), top + 28, Math.min(left + W, seg + 80), top + 30, ACCENT);

        // sidebar
        g.fill(left, top + 30, left + SIDE, top + H, 0xFF0D0E1A);
        g.fill(left + SIDE, top + 30, left + SIDE + 1, top + H, 0xFF23274A);
        for (int i = 0; i < TABS.length; i++) {
            int x = left + 4, y = top + 38 + i * 26, w = SIDE - 8;
            boolean sel = tab == i;
            boolean hov = in(mx, my, x, y, w, 22);
            if (sel) rrect(g, x, y, w, 22, 0xFF1D2347);
            else if (hov) rrect(g, x, y, w, 22, 0xFF151833);
            if (sel) g.fill(x, y + 3, x + 2, y + 19, ACCENT);
            txt(g, TABS[i], x + 10, y + 7, sel ? 0xFFFFFFFF : 0xFF8A90B8);
            final int idx = i;
            hits.add(new Hit(x, y, w, 22, () -> {
                tab = idx;
                page = 0;
                reopen();
            }));
        }

        // sidebar footer: live status + total profit
        AutoSeller.Status st = AutoSeller.status;
        int fy = top + H - 38;
        g.fill(left + 8, fy - 6, left + SIDE - 8, fy - 5, 0xFF23274A);
        rrect(g, left + 8, fy + 2, 6, 6, pulsing(st));
        txt(g, st.name().replace('_', ' '), left + 18, fy, statusColor(st));
        txt(g, "Total", left + 8, fy + 13, 0xFF6C7298);
        long total = Profit.total();
        txt(g, textRenderer.trimToWidth(Profit.signed(total), SIDE - 14), left + 8, fy + 23, profitColor(total));
    }

    private void title(DrawContext g, String s, int y) {
        big(g, s, cx, y, 1.3f, 0xFFFFFFFF);
    }

    private void renderDashboard(DrawContext g, int mx, int my) {
        Config c = Config.INSTANCE;
        AutoSeller.Status st = AutoSeller.status;
        int y0 = top + 36;
        title(g, "Dashboard", y0);

        // status card
        int y = y0 + 18;
        rrect(g, cx, y, cw, 46, 0xFF1A1D33);
        g.fill(cx + 1, y + 3, cx + 4, y + 43, statusColor(st));
        rrect(g, cx + 12, y + 9, 8, 8, pulsing(st));
        big(g, st.name().replace('_', ' '), cx + 26, y + 8, 1.4f, statusColor(st));
        txt(g, textRenderer.trimToWidth(AutoSeller.message, cw - 20), cx + 12, y + 30,
                st == AutoSeller.Status.ERROR ? 0xFFFF8D90 : 0xFFA6ABCB);

        // stat cards
        y += 54;
        int cwid = (cw - 8) / 3;
        card(g, cx, y, cwid, 36, "CHEAPEST", money(AutoSeller.cheapest), 0xFFFFFFFF);
        card(g, cx + cwid + 4, y, cwid, 36, "SELLING AT", money(AutoSeller.sellPrice), 0xFF4DD8FF);
        long total = Profit.total();
        card(g, cx + 2 * (cwid + 4), y, cwid, 36, "PROFIT", Profit.signed(total), profitColor(total));

        // start / stop
        y += 44;
        boolean on = c.enabled;
        button(g, mx, my, cx, y, cw, 28, on ? "STOP" : "START",
                on ? 0xFFEF5358 : 0xFF38CC7B, on ? 0xFF9E2B30 : 0xFF1C8A50, () -> {
                    c.enabled = !c.enabled;
                    c.save();
                    if (c.enabled) close();
                });

        // quick info
        y += 36;
        txt(g, "Auto Sell: " + (c.autoSell ? "ON" : "OFF") + "   Auto-pick bed: " + (c.autoPickBed ? "ON" : "OFF"),
                cx, y, 0xFF8A90B8);
        txt(g, textRenderer.trimToWidth("Undercut " + Profit.money(c.undercut) + "   Min " + Profit.money(c.minPrice), cw),
                cx, y + 12, 0xFF8A90B8);
        txt(g, "Menu key: " + DonutBedClient.openKey.getBoundKeyLocalizedText().getString(), cx, y + 24, 0xFF6C7298);
    }

    private void renderSettings(DrawContext g, int mx, int my) {
        Config c = Config.INSTANCE;
        int y0 = top + 36;
        title(g, "Settings", y0);

        int y = y0 + 16;
        toggleRow(g, mx, my, cx, y, cw, "Auto Sell", c.autoSell, () -> { c.autoSell = !c.autoSell; c.save(); });
        toggleRow(g, mx, my, cx, y + 22, cw, "Chat notifications", c.notifications, () -> { c.notifications = !c.notifications; c.save(); });
        toggleRow(g, mx, my, cx, y + 44, cw, "Auto-pick bed", c.autoPickBed, () -> { c.autoPickBed = !c.autoPickBed; c.save(); });

        String[] labels = { "Undercut ($)", "Minimum price ($)", "Scan delay (ms)", "Auction timeout (s)", "Cooldown (s)" };
        int base = y + 3 * 22 + 6;
        for (int i = 0; i < labels.length; i++) {
            rrect(g, cx, base + i * 21, cw, 20, 0xFF1A1D33);
            txt(g, labels[i], cx + 8, base + i * 21 + 6, 0xFFE6E8FF);
        }
    }

    private void renderProfit(DrawContext g, int mx, int my) {
        int y0 = top + 36;
        title(g, "Profit", y0);

        rrect(g, cx, y0 + 16, cw, 20, 0xFF1A1D33);
        txt(g, "Cost per bed ($)", cx + 8, y0 + 22, 0xFFE6E8FF);

        long total = Profit.total();
        txt(g, "TOTAL PROFIT", cx, y0 + 42, 0xFF8A90B8);
        big(g, Profit.signed(total), cx, y0 + 52, 2f, profitColor(total));

        int y = y0 + 76;
        int cwid = (cw - 8) / 3;
        long today = Profit.since(Profit.startOfToday());
        long week = Profit.since(System.currentTimeMillis() - 7L * 24 * 3600 * 1000);
        card(g, cx, y, cwid, 34, "TODAY", Profit.signed(today), profitColor(today));
        card(g, cx + cwid + 4, y, cwid, 34, "7 DAYS", Profit.signed(week), profitColor(week));
        card(g, cx + 2 * (cwid + 4), y, cwid, 34, "AVG / BED", Profit.signed(Profit.average()), profitColor(Profit.average()));

        txt(g, "Beds listed: " + Profit.count() + "     Best: " + Profit.signed(Profit.best()), cx, y + 40, 0xFF8A90B8);

        // 7-day bar chart
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

        // reset
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

    private void renderHistory(DrawContext g, int mx, int my) {
        int y0 = top + 36;
        title(g, "History", y0);
        txtRight(g, Profit.count() + " sales", cx + cw, y0 + 4, 0xFF8A90B8);

        txt(g, "DATE", cx + 4, y0 + 20, ACCENT);
        txt(g, "LISTED AT", cx + 128, y0 + 20, ACCENT);
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
            txt(g, Profit.money(e.price), cx + 128, y + 1, 0xFFFFFFFF);
            txtRight(g, Profit.signed(e.profit), cx + cw - 4, y + 1, profitColor(e.profit));
        }

        int pages = Math.max(1, (list.size() + ROWS - 1) / ROWS);
        page = Math.min(page, pages - 1);
        int by = top + H - 24;
        button(g, mx, my, cx, by, 60, 16, "< Newer", page > 0 ? 0xFF3A3F5C : 0xFF202338, page > 0 ? 0xFF262A44 : 0xFF181A2C, () -> {
            if (page > 0) page--;
        });
        button(g, mx, my, cx + cw - 60, by, 60, 16, "Older >", page < pages - 1 ? 0xFF3A3F5C : 0xFF202338,
                page < pages - 1 ? 0xFF262A44 : 0xFF181A2C, () -> {
                    if (page < pages - 1) page++;
                });
        g.drawCenteredTextWithShadow(textRenderer, Text.literal("Page " + (page + 1) + "/" + pages),
                cx + cw / 2, by + 4, 0xFF8A90B8);
    }
}
