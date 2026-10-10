package com.marketscout;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MarketScreen extends Screen {
    private static final int W = 360;
    private static final int H = 252;
    private static final int SIDE = 80;

    // tabs: 0 dashboard, 1 market, 2 watchlist, 3 deals, 4 prices, 5 profit, 6 chat, 7 settings, 8 alerts
    private static final String[] TABS = { "Dashboard", "Market", "Watchlist", "Deals", "Prices", "Profit", "Chat", "Settings", "Alerts" };
    private static final String[] RANGES = { "1H", "1D", "7D", "All" };
    private static final long[] RANGE_MS = { 3_600_000L, 86_400_000L, 7L * 86_400_000L, 0L };
    private static final String[] VIEWS = { "Summary", "History", "Add" };

    private static final int[] THEME = { 0xFF4C8DFF, 0xFFA066FF, 0xFF35D07F, 0xFFFF9F43, 0xFFFF6EB4, 0xFFFF5D62, 0xFF2FD6D6 };
    private static final String[] THEME_NAMES = { "Ocean", "Violet", "Mint", "Sunset", "Bubblegum", "Ruby", "Aqua" };

    private static final String[][] ICONS = {
            { "###.###.", "###.###.", "###.###.", "........", "###.###.", "###.###.", "###.###.", "........" },
            { "........", "......#.", "..#...#.", "..#.#.#.", "..#.#.#.", "#.#.#.#.", "#.#.#.#.", "########" },
            { "..####..", ".#.##.#.", "#..##..#", "########", "#......#", "#......#", "#......#", "########" },
            { "....##..", "...##...", "..##....", ".######.", "...##...", "..##....", ".##.....", ".#......" },
            { "#.......", "#.....#.", "#..#.#.#", "#.#.#..#", "##.#....", "#.......", "#.......", "########" },
            { "...##...", "..####..", ".##.##..", "..###...", "...###..", ".##.##..", "..####..", "...##..." },
            { "########", "#......#", "#......#", "#......#", "########", "..#.....", ".#......", "#......." },
            { "..#.....", "########", "..#.....", "......#.", "########", "......#.", "...#....", "########" },
            { "...##...", "..####..", "..####..", ".######.", ".######.", "########", "........", "...##..." }
    };

    private static final String[] LOGO = {
            "...####...", "..######..", ".###..###.", "###....###", "##......##",
            "##......##", "###....###", ".###..###.", "..######..", "...####..." };

    private static final Map<String, String> TIPS = new HashMap<>();
    static {
        TIPS.put("Overlay", "Highlights deals on the real auction screen.");
        TIPS.put("Price labels", "Shows the per-item price on every listing.");
        TIPS.put("Auto sort", "Clicks the sort button so prices run lowest-first.");
        TIPS.put("Per-item", "Divides stack prices by the stack size.");
        TIPS.put("Min discount (%)", "How far under the median counts as a deal.");
        TIPS.put("Min deal profit ($)", "Only alert when the estimated profit is this much.");
        TIPS.put("Scan cooldown (s)", "Pause between auction scans.");
        TIPS.put("Scan delay (ms)", "Wait after the GUI opens before reading it.");
        TIPS.put("Auction timeout (s)", "Give up waiting for the GUI after this.");
        TIPS.put("Max pages to read", "Pages to read when it can't sort.");
        TIPS.put("Sale tax (%)", "Server fee taken when you sell (for profit maths).");
        TIPS.put("Resell undercut ($)", "How far under the market you'd relist.");
        TIPS.put("Search term (/ah ...)", "The word sent to /ah to find this item.");
        TIPS.put("Alert below ($)", "Alert when the cheapest listing is this or less.");
        TIPS.put("Track buys/sales from chat", "Reads chat messages to log your flips.");
        TIPS.put("Chat notifications", "Messages in chat when something happens.");
        TIPS.put("Sound alert", "Plays a sound for deals and flips.");
        TIPS.put("Popup toast", "Shows a popup in the corner.");
    }

    private static int tab = 0;
    private static int page = 0;
    private static int watchSel = 0;
    private static int priceSel = 0;
    private static int priceRange = 1;
    private static int profitView = 0;
    private static String note = "";
    private static long noteUntil = 0;
    private static String newTerm = "";
    private static String manItem = "";
    private static String manBuy = "";
    private static String manSell = "";
    private static float tabHiY = -1f;
    private static long tabSwitchAt = 0;
    private static boolean skipAnim = false;
    private static final Map<String, Float> ANIM = new HashMap<>();
    private static final Map<String, Double> NUMS = new HashMap<>();

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private final List<TextFieldWidget> fields = new ArrayList<>();
    private final long openAt;
    private boolean resetArmed = false;
    private String tip = null;
    private int left, top, cx, cw;

    public MarketScreen() {
        super(Text.literal("Market Scout"));
        openAt = skipAnim ? 0 : System.currentTimeMillis();
        skipAnim = false;
    }

    private void reopen() {
        skipAnim = true;
        tabSwitchAt = System.currentTimeMillis();
        if (client != null) client.setScreen(new MarketScreen());
    }

    private static void say(String s) {
        note = s;
        noteUntil = System.currentTimeMillis() + 4000;
    }

    private static Config.Watch selWatch() {
        List<Config.Watch> l = Config.INSTANCE.watch;
        watchSel = Math.max(0, Math.min(watchSel, l.size() - 1));
        return l.get(watchSel);
    }

    private static int acc() {
        return THEME[Math.max(0, Math.min(THEME.length - 1, Config.INSTANCE.theme))];
    }

    private static long num(String s, long old) {
        if (s == null || s.isEmpty()) return old;
        try { return Long.parseLong(s); } catch (Exception e) { return old; }
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

        if (tab == 2) {
            textField(cx, y0 + 17, cw - 72, 40, newTerm, s -> newTerm = s);
            Config.Watch w = selWatch();
            textField(cx + cw - 126, y0 + 109, 120, 40, w.search, s -> w.search = s);
            numField(fx, y0 + 131, 80, w.alertBelow == 0 ? "" : String.valueOf(w.alertBelow), s -> w.alertBelow = s.isEmpty() ? 0 : num(s, w.alertBelow));
        } else if (tab == 3) {
            numField(fx, y0 + 17, 80, String.valueOf(c.dealPercent), s -> c.dealPercent = (int) Math.min(num(s, c.dealPercent), 99));
            numField(fx, y0 + 39, 80, String.valueOf(c.minDealProfit), s -> c.minDealProfit = num(s, c.minDealProfit));
        } else if (tab == 5 && profitView == 2) {
            textField(cx + cw - 126, y0 + 41, 120, 40, manItem, s -> manItem = s);
            numField(fx, y0 + 62, 80, manBuy, s -> manBuy = s);
            numField(fx, y0 + 83, 80, manSell, s -> manSell = s);
        } else if (tab == 6) {
            textField(cx, y0 + 52, cw, 300, c.buyRegex, s -> c.buyRegex = s);
            textField(cx, y0 + 86, cw, 300, c.sellRegex, s -> c.sellRegex = s);
        } else if (tab == 7) {
            int base = y0 + 16 + 2 * 22 + 4;
            numField(fx, base + 1, 80, String.valueOf(c.cooldownMs / 1000), s -> c.cooldownMs = (int) Math.min(num(s, c.cooldownMs / 1000) * 1000, 1_000_000));
            numField(fx, base + 21, 80, String.valueOf(c.scanDelayMs), s -> c.scanDelayMs = (int) Math.min(num(s, c.scanDelayMs), 1_000_000));
            numField(fx, base + 41, 80, String.valueOf(c.timeoutMs / 1000), s -> c.timeoutMs = (int) Math.min(num(s, c.timeoutMs / 1000) * 1000, 1_000_000));
            numField(fx, base + 61, 80, String.valueOf(c.scanPages), s -> c.scanPages = (int) Math.min(num(s, c.scanPages), 100));
            decField(fx, base + 81, 80, String.valueOf(c.saleTaxPercent), s -> {
                try { c.saleTaxPercent = Math.min(90.0, Double.parseDouble(s)); } catch (Exception ignored) {}
            });
            numField(fx, base + 101, 80, String.valueOf(c.resellUndercut), s -> c.resellUndercut = num(s, c.resellUndercut));
        } else if (tab == 8) {
            textField(cx, y0 + 100, cw, 200, c.discordWebhook, s -> c.discordWebhook = s.trim());
        }
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

    private void decField(int x, int y, int w, String value, Consumer<String> onChange) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 18, Text.empty());
        f.setMaxLength(6);
        f.setTextPredicate(s -> s.matches("\\d{0,2}(\\.\\d{0,2})?"));
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
        Chat.compile();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean typing = fields.stream().anyMatch(TextFieldWidget::isFocused);
        if (!typing && MarketScout.openKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        if (!typing && keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + TABS.length) {
            tab = keyCode - GLFW.GLFW_KEY_1;
            page = 0;
            reopen();
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

    // ------------------------------------------------------------ helpers

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

    private static int alpha(int rgb, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    private static float ease(float p) {
        float q = 1f - Math.max(0f, Math.min(1f, p));
        return 1f - q * q * q;
    }

    private static float anim(String key, float target, float speed) {
        float cur = ANIM.getOrDefault(key, target);
        cur += (target - cur) * speed;
        ANIM.put(key, cur);
        return cur;
    }

    private static long animNum(String key, long target) {
        double cur = NUMS.getOrDefault(key, (double) target);
        cur += (target - cur) * 0.18;
        if (Math.abs(target - cur) < 1) cur = target;
        NUMS.put(key, cur);
        return Math.round(cur);
    }

    private static void rrect(DrawContext g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + 1, color);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, color);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
    }

    private void bitmap(DrawContext g, String[] rows, int x, int y, int color) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int k = 0; k < row.length(); k++) {
                if (row.charAt(k) == '#') g.fill(x + k, y + r, x + k + 1, y + r + 1, color);
            }
        }
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
        float hv = anim("btn" + x + "_" + y, hov ? 1f : 0f, 0.3f);
        int d = Math.round(hv * 34);
        rrect(g, x, y, w, h, shade(bottom1, -40 + d));
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, shade(top1, d), shade(bottom1, d));
        g.fill(x + 2, y + 1, x + w - 2, y + 2, alpha(0xFFFFFF, 30 + Math.round(hv * 40)));
        g.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        hits.add(new Hit(x, y, w, h, action));
    }

    private void smallButton(DrawContext g, int mx, int my, int x, int y, int w, int h, String label, boolean active, Runnable action) {
        button(g, mx, my, x, y, w, h, label, active ? acc() : 0xFF3A3F5C, active ? shade(acc(), -75) : 0xFF262A44, action);
    }

    private void fieldRow(DrawContext g, int mx, int my, int x, int y, int w, int h, String label) {
        boolean hov = in(mx, my, x, y, w, h);
        rrect(g, x, y, w, h, hov ? 0xFF202448 : 0xFF1A1D33);
        if (hov) g.fill(x + 1, y + 3, x + 3, y + h - 3, acc());
        txt(g, label, x + 8, y + (h - 8) / 2 + 1, 0xFFE6E8FF);
        if (hov && TIPS.containsKey(label)) tip = TIPS.get(label);
    }

    private void toggleRow(DrawContext g, int mx, int my, int x, int y, int w,
                           String label, boolean val, Runnable click) {
        boolean hov = in(mx, my, x, y, w, 20);
        rrect(g, x, y, w, 20, hov ? 0xFF22264A : 0xFF1A1D33);
        if (hov) g.fill(x + 1, y + 3, x + 3, y + 17, acc());
        txt(g, label, x + 8, y + 6, 0xFFE6E8FF);
        float cur = anim("tg" + label, val ? 1f : 0f, 0.25f);
        int tx = x + w - 36, ty = y + 4;
        if (cur > 0.05f) rrect(g, tx - 1, ty - 1, 30, 14, lerp(0xFF1A1D33, 0xFF1E5A3E, cur));
        rrect(g, tx, ty, 28, 12, lerp(0xFF3A3F5C, 0xFF2FBF71, cur));
        rrect(g, tx + 2 + Math.round(cur * 16), ty + 2, 8, 8, 0xFFFFFFFF);
        if (hov && TIPS.containsKey(label)) tip = TIPS.get(label);
        hits.add(new Hit(x, y, w, 20, click));
    }

    private void card(DrawContext g, int x, int y, int w, int h, String label, String value, int vcolor) {
        rrect(g, x, y, w, h, 0xFF1A1D33);
        g.fillGradient(x + 1, y + 2, x + w - 1, y + 10, alpha(vcolor, 28), alpha(vcolor, 0));
        g.fill(x + 1, y + 3, x + 3, y + h - 3, vcolor);
        txt(g, label, x + 8, y + 6, 0xFF8A90B8);
        txt(g, textRenderer.trimToWidth(value, w - 12), x + 8, y + h - 14, vcolor);
    }

    private static int profitColor(long v) { return v >= 0 ? 0xFF4DE08A : 0xFFFF5D62; }

    private static String money(long v) { return v < 0 ? "-" : Fmt.compact(v); }

    private int statusColor() {
        Scout.Status st = Scout.status;
        if (st == Scout.Status.SCANNING) return 0xFFFFD84D;
        if (st == Scout.Status.ERROR) return 0xFFFF5D62;
        return Config.INSTANCE.scouting ? 0xFF4DE08A : 0xFF9AA0C0;
    }

    private String statusName() {
        Scout.Status st = Scout.status;
        if (st == Scout.Status.SCANNING) return "SCANNING";
        if (st == Scout.Status.ERROR) return "ERROR";
        return Config.INSTANCE.scouting ? "WATCHING" : "STOPPED";
    }

    private int pulsingStatus() {
        int base = statusColor();
        if (Scout.status == Scout.Status.SCANNING || (Config.INSTANCE.scouting && Scout.status == Scout.Status.IDLE)) {
            float p = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 220.0);
            return lerp(base, 0xFF202438, p * 0.7f);
        }
        return base;
    }

    private void spinner(DrawContext g, int cxp, int cyp, int r, int color) {
        int n = 10;
        int head = (int) ((System.currentTimeMillis() / 90) % n);
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / n;
            int x = cxp + (int) Math.round(Math.cos(ang) * r);
            int y = cyp + (int) Math.round(Math.sin(ang) * r);
            int d = (head - i + n) % n;
            float f = 1f - d / (float) n;
            g.fill(x - 1, y - 1, x + 1, y + 1, alpha(color, 50 + Math.round(205 * f)));
        }
    }

    private void sparkline(DrawContext g, int x, int y, int w, int h) {
        List<Flips.Entry> list = Flips.entries();
        int n = Math.min(24, list.size());
        if (n < 2) {
            g.fill(x, y + h / 2, x + w, y + h / 2 + 1, 0xFF2B2F52);
            return;
        }
        long[] cum = new long[n];
        long run = 0;
        for (int i = 0; i < n; i++) {
            run += list.get(list.size() - n + i).profit;
            cum[i] = run;
        }
        long lo = Long.MAX_VALUE, hi = Long.MIN_VALUE;
        for (long v : cum) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        if (hi == lo) { hi++; lo--; }
        int color = profitColor(cum[n - 1] - cum[0]);
        int prev = -1;
        for (int col = 0; col < w; col++) {
            double f = col * (n - 1) / (double) Math.max(1, w - 1);
            int i = (int) f;
            double frac = f - i;
            double v = cum[i] + (cum[Math.min(i + 1, n - 1)] - cum[i]) * frac;
            int py = y + h - 1 - (int) Math.round((v - lo) / (double) (hi - lo) * (h - 1));
            g.fill(x + col, py, x + col + 1, y + h, alpha(color, 36));
            int a = prev < 0 ? py : Math.min(py, prev);
            int b = (prev < 0 ? py : Math.max(py, prev)) + 1;
            g.fill(x + col, a, x + col + 1, b + 1, color);
            prev = py;
        }
    }

    // ------------------------------------------------------------ rendering

    @Override
    public void renderBackground(DrawContext g, int mx, int my, float delta) {
        long t = System.currentTimeMillis();
        for (int i = 1; i <= 6; i++) {
            g.fill(left - i, top - i, left + W + i, top + H + i, alpha(0x000000, 14));
        }
        float glow = 0.5f + 0.5f * (float) Math.sin(t / 700.0);
        rrect(g, left - 1, top - 1, W + 2, H + 2, lerp(shade(acc(), -90), shade(acc(), -25), glow));
        g.fillGradient(left, top, left + W, top + H, 0xFA171A2E, 0xFA0A0B15);
        for (int i = 0; i < 30; i++) {
            int px = left + SIDE + 4 + (int) ((i * 61L) % (W - SIDE - 8)) + (int) Math.round(Math.sin(t / 900.0 + i) * 6);
            int span = H - 34;
            int py = top + H - (int) (((t / (22 + (i % 7) * 9)) + i * 53L) % span);
            int size = (i % 3 == 0) ? 2 : 1;
            if (px < left + SIDE + 2 || px > left + W - 4) continue;
            g.fill(px, py, px + size, py + size, alpha(i % 2 == 0 ? acc() : 0xFFFFFF, i % 2 == 0 ? 70 : 36));
        }
    }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        hits.clear();
        tip = null;
        long now = System.currentTimeMillis();
        g.fillGradient(0, 0, width, height, 0xB4090A14, 0xD2030308);

        float p = openAt == 0 ? 1f : Math.min(1f, (now - openAt) / 240f);
        boolean opening = p < 1f;
        for (TextFieldWidget f : fields) f.visible = !opening;

        var m = g.getMatrices();
        m.push();
        if (opening) {
            float s = 0.9f + 0.1f * ease(p);
            m.translate(width / 2f, height / 2f, 0);
            m.scale(s, s, 1f);
            m.translate(-width / 2f, -height / 2f, 0);
        }

        super.render(g, mx, my, delta);
        drawChrome(g, mx, my);
        switch (tab) {
            case 0 -> renderDashboard(g, mx, my);
            case 1 -> renderMarket(g, mx, my);
            case 2 -> renderWatchlist(g, mx, my);
            case 3 -> renderDeals(g, mx, my);
            case 4 -> renderPrices(g, mx, my);
            case 5 -> renderProfit(g, mx, my);
            case 6 -> renderChat(g, mx, my);
            case 7 -> renderSettings(g, mx, my);
            default -> renderAlerts(g, mx, my);
        }

        float tf = Math.min(1f, (now - tabSwitchAt) / 200f);
        if (tf < 1f) {
            g.fill(cx - 6, top + 31, left + W, top + H, (((int) ((1f - tf) * 235)) << 24) | 0x121428);
        }
        if (opening) g.fill(left - 8, top - 8, left + W + 8, top + H + 8, (((int) ((1f - p) * 200)) << 24) | 0x05060C);
        m.pop();

        if (tip != null && !opening) {
            int tw = textRenderer.getWidth(tip) + 12;
            int tx = Math.min(mx + 10, width - tw - 4);
            int ty = Math.min(my + 12, height - 20);
            rrect(g, tx - 1, ty - 1, tw + 2, 18, acc());
            rrect(g, tx, ty, tw, 16, 0xFF0E1020);
            txt(g, tip, tx + 6, ty + 4, 0xFFE6E8FF);
        }
    }

    private void drawChrome(DrawContext g, int mx, int my) {
        int a = acc();
        long t = System.currentTimeMillis();

        g.fillGradient(left, top, left + W, top + 28, shade(a, -50), shade(a, -140));
        float bob = (float) Math.sin(t / 500.0);
        bitmap(g, LOGO, left + 9, top + 9 + Math.round(bob * 0.8f), 0xFF7DFFB0);
        g.fill(left + 13, top + 13, left + 15, top + 15, 0xFFFFFFFF);
        big(g, "MARKET SCOUT", left + 26, top + 9, 1.25f, 0xFFFFFFFF);
        for (int i = 0; i < THEME.length; i++) {
            int x = left + W - 8 - (THEME.length - i) * 11 + 3;
            int y = top + 10;
            boolean sel = Config.INSTANCE.theme == i;
            boolean hov = in(mx, my, x - 1, y - 1, 10, 10);
            if (sel) g.fill(x - 1, y - 1, x + 9, y + 9, 0xFFFFFFFF);
            rrect(g, x, y, 8, 8, hov ? shade(THEME[i], 30) : THEME[i]);
            if (hov) tip = "Theme: " + THEME_NAMES[i];
            final int idx = i;
            hits.add(new Hit(x - 1, y - 1, 10, 10, () -> { Config.INSTANCE.theme = idx; Config.INSTANCE.save(); }));
        }
        g.fill(left, top + 28, left + W, top + 30, 0xFF23274A);
        int seg = left + (int) ((t / 6) % (W + 80)) - 80;
        g.fill(Math.max(left, seg), top + 28, Math.min(left + W, seg + 80), top + 30, a);

        g.fill(left, top + 30, left + SIDE, top + H, 0xFF0D0E1A);
        g.fill(left + SIDE, top + 30, left + SIDE + 1, top + H, 0xFF23274A);

        float target = top + 34 + tab * 19;
        tabHiY = tabHiY < 0 ? target : tabHiY + (target - tabHiY) * 0.3f;
        rrect(g, left + 4, Math.round(tabHiY), SIDE - 8, 17, 0xFF1D2347);
        g.fill(left + 4, Math.round(tabHiY) + 3, left + 6, Math.round(tabHiY) + 14, a);

        for (int i = 0; i < TABS.length; i++) {
            int x = left + 4, y = top + 34 + i * 19, w = SIDE - 8;
            boolean sel = tab == i;
            boolean hov = in(mx, my, x, y, w, 17);
            if (hov && !sel) rrect(g, x, y, w, 17, 0xFF151833);
            int ic = sel ? a : (hov ? 0xFFC8CCE8 : 0xFF6C7298);
            bitmap(g, ICONS[i], x + 6, y + 4, ic);
            txt(g, TABS[i], x + 18, y + 5, sel ? 0xFFFFFFFF : (hov ? 0xFFDDE0F5 : 0xFF8A90B8));
            final int idx = i;
            hits.add(new Hit(x, y, w, 17, () -> {
                tab = idx;
                page = 0;
                reopen();
            }));
        }

        int fy = top + H - 38;
        g.fill(left + 8, fy - 6, left + SIDE - 8, fy - 5, 0xFF23274A);
        rrect(g, left + 8, fy + 2, 6, 6, pulsingStatus());
        txt(g, statusName(), left + 18, fy, statusColor());
        txt(g, "Flip profit", left + 8, fy + 13, 0xFF6C7298);
        long total = animNum("sideTotal", Flips.total());
        txt(g, textRenderer.trimToWidth(Fmt.compactSigned(total), SIDE - 14), left + 8, fy + 23, profitColor(total));
    }

    private void title(DrawContext g, String s, int y) {
        big(g, s, cx, y, 1.3f, 0xFFFFFFFF);
        g.fill(cx, y + 12, cx + 22, y + 13, acc());
    }

    // ---- dashboard

    private void renderDashboard(DrawContext g, int mx, int my) {
        Config c = Config.INSTANCE;
        Scout.Status st = Scout.status;
        int y0 = top + 36;
        title(g, "Dashboard", y0);

        int y = y0 + 17;
        int sc = statusColor();
        rrect(g, cx, y, cw, 44, 0xFF1A1D33);
        g.fillGradient(cx + 1, y + 1, cx + cw - 1, y + 22, alpha(sc, 26), alpha(sc, 0));
        g.fill(cx + 1, y + 3, cx + 4, y + 41, sc);
        rrect(g, cx + 12, y + 9, 8, 8, pulsingStatus());
        big(g, statusName(), cx + 26, y + 8, 1.4f, sc);
        int enabled = 0;
        for (Config.Watch w : c.watch) if (w.enabled) enabled++;
        txt(g, st == Scout.Status.SCANNING ? "Scanning: " + Scout.currentName : "Watching " + enabled + " item(s)", cx + 12, y + 29, 0xFF8A90B8);
        if (st == Scout.Status.SCANNING) spinner(g, cx + cw - 26, y + 22, 9, sc);
        else sparkline(g, cx + cw - 100, y + 8, 88, 28);
        txt(g, textRenderer.trimToWidth(Scout.message, cw), cx + 2, y + 48,
                st == Scout.Status.ERROR ? 0xFFFF8D90 : 0xFFA6ABCB);

        y += 62;
        int cwid = (cw - 8) / 3;
        Scout.Snap best = null;
        for (Scout.Snap s : Scout.data.snaps.values()) if (best == null || s.potential > best.potential) best = s;
        String bestTxt = best == null || best.potential <= 0 ? "none yet" : best.name + " " + Fmt.compactSigned(best.potential);
        card(g, cx, y, cwid, 34, "BEST FLIP NOW", bestTxt, 0xFF4DE08A);
        card(g, cx + cwid + 4, y, cwid, 34, "DEALS FOUND", String.valueOf(Deals.entries().size()), 0xFF4DD8FF);
        long total = animNum("dashTotal", Flips.total());
        card(g, cx + 2 * (cwid + 4), y, cwid, 34, "FLIP PROFIT", Fmt.compactSigned(total), profitColor(total));

        y += 40;
        boolean on = c.scouting;
        if (on) {
            float glow = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
            rrect(g, cx - 2, y - 2, cw + 4, 30, lerp(0xFF1A1D33, 0xFF7A2A30, 0.4f + glow * 0.6f));
        }
        button(g, mx, my, cx, y, cw, 26, on ? "STOP SCOUT" : "START SCOUT",
                on ? 0xFFEF5358 : 0xFF38CC7B, on ? 0xFF9E2B30 : 0xFF1C8A50, () -> {
                    c.scouting = !c.scouting;
                    c.save();
                    if (c.scouting) close();
                });

        y += 32;
        int hw = (cw - 4) / 2;
        toggleRow(g, mx, my, cx, y, hw, "Overlay", c.overlay, () -> { c.overlay = !c.overlay; c.save(); });
        toggleRow(g, mx, my, cx + hw + 4, y, hw, "Auto sort", c.autoSort, () -> { c.autoSort = !c.autoSort; c.save(); });

        y += 26;
        long remaining = Math.max(0, Scout.nextAllowed - System.currentTimeMillis());
        float frac = c.scouting && c.cooldownMs > 0 ? 1f - Math.min(1f, remaining / (float) c.cooldownMs) : 0f;
        rrect(g, cx, y, cw, 6, 0xFF12142A);
        if (frac > 0.02f) g.fillGradient(cx + 1, y + 1, cx + 1 + Math.round((cw - 2) * frac), y + 5, acc(), shade(acc(), -80));
        txt(g, c.scouting ? (remaining > 0 ? "Next scan in " + String.format("%.1fs", remaining / 1000.0) : "Ready")
                : "Stopped - press START SCOUT", cx, y + 9, 0xFF6C7298);
        txtRight(g, MarketScout.openKey.getBoundKeyLocalizedText().getString() + " = menu   1-9 = tabs", cx + cw, y + 9, 0xFF4A5078);
    }

    // ---- market (ranked flips)

    private void renderMarket(DrawContext g, int mx, int my) {
        int y0 = top + 36;
        title(g, "Market", y0);
        smallButton(g, mx, my, cx + cw - 70, y0 + 1, 70, 14, "Scan all now", true, () -> {
            Scout.scanAll();
            say("Queued all watchlist items. Close the menu to start.");
        });

        txt(g, "ITEM", cx + 4, y0 + 20, acc());
        txt(g, "CHEAPEST", cx + 58, y0 + 20, acc());
        txt(g, "RESELL", cx + 108, y0 + 20, acc());
        txt(g, "PROFIT", cx + 158, y0 + 20, acc());
        txt(g, "DISC", cx + 206, y0 + 20, acc());
        txtRight(g, "AGE", cx + cw - 4, y0 + 20, acc());
        g.fill(cx, y0 + 31, cx + cw, y0 + 32, 0xFF2B2F52);

        List<Scout.Snap> ranked = new ArrayList<>(Scout.data.snaps.values());
        ranked.sort((a, b) -> {
            int c1 = Long.compare(b.potential, a.potential);
            return c1 != 0 ? c1 : Integer.compare(b.discount, a.discount);
        });
        if (ranked.isEmpty()) {
            txt(g, "No scans yet. Add items in Watchlist, then", cx + 4, y0 + 42, 0xFF8A90B8);
            txt(g, "press START SCOUT or Scan all now.", cx + 4, y0 + 54, 0xFF8A90B8);
        }
        for (int i = 0; i < 12 && i < ranked.size(); i++) {
            Scout.Snap s = ranked.get(i);
            int y = y0 + 34 + i * 13;
            boolean hov = in(mx, my, cx, y - 1, cw, 12);
            g.fill(cx, y - 1, cx + cw, y + 11, hov ? 0xFF22264A : (i % 2 == 0 ? 0xFF161930 : 0x00000000));
            txt(g, textRenderer.trimToWidth(s.name, 50), cx + 4, y + 1, 0xFFB7C6F5);
            txt(g, Fmt.compact(s.cheapest), cx + 58, y + 1, 0xFFFFFFFF);
            txt(g, Fmt.compact(s.resell), cx + 108, y + 1, 0xFFB7C6F5);
            txt(g, s.potential > 0 ? Fmt.compactSigned(s.potential) : "-", cx + 158, y + 1, s.potential > 0 ? 0xFF4DE08A : 0xFF6C7298);
            txt(g, s.discount + "%", cx + 206, y + 1, s.discount >= Config.INSTANCE.dealPercent ? 0xFFFFD84D : 0xFF8A90B8);
            txtRight(g, Fmt.age(s.time), cx + cw - 4, y + 1, 0xFF6C7298);
            final String name = s.name;
            hits.add(new Hit(cx, y - 1, cw, 12, () -> { Scout.scanOne(name); say("Queued " + name + ". Close the menu to scan it."); }));
            if (hov) tip = "Click to rescan " + s.name;
        }
        boolean showNote = System.currentTimeMillis() < noteUntil;
        txt(g, showNote ? note : "Click a row to rescan it. Ranked by estimated profit.", cx, y0 + 196,
                showNote ? 0xFFFFD84D : 0xFF6C7298);
    }

    // ---- watchlist

    private void renderWatchlist(DrawContext g, int mx, int my) {
        Config c = Config.INSTANCE;
        int y0 = top + 36;
        title(g, "Watchlist", y0);
        List<Config.Watch> list = c.watch;
        int n = list.size();
        Config.Watch w = selWatch();

        // add by typing
        if (newTerm.isEmpty()) txt(g, "type an item to watch...", cx + 5, y0 + 22, 0xFF4A5078);
        smallButton(g, mx, my, cx + cw - 68, y0 + 16, 68, 20, "+ Add", true, () -> {
            String t = newTerm.trim();
            if (t.isEmpty()) { say("Type an item name first."); return; }
            Config.Watch nw = new Config.Watch();
            nw.name = t.substring(0, 1).toUpperCase() + t.substring(1);
            nw.search = t;
            list.add(nw);
            watchSel = list.size() - 1;
            newTerm = "";
            c.save();
            say("Added " + nw.name);
            reopen();
        });

        int sy = y0 + 42;
        smallButton(g, mx, my, cx, sy, 24, 20, "<", true, () -> { watchSel = (watchSel - 1 + n) % n; reopen(); });
        smallButton(g, mx, my, cx + cw - 24, sy, 24, 20, ">", true, () -> { watchSel = (watchSel + 1) % n; reopen(); });
        rrect(g, cx + 28, sy, cw - 56, 20, 0xFF1A1D33);
        g.drawCenteredTextWithShadow(textRenderer,
                Text.literal(textRenderer.trimToWidth(w.name, cw - 100) + "  (" + (watchSel + 1) + "/" + n + ")"),
                cx + cw / 2, sy + 6, 0xFFFFFFFF);

        toggleRow(g, mx, my, cx, y0 + 66, cw, "Watching this item", w.enabled, () -> { w.enabled = !w.enabled; c.save(); });

        rrect(g, cx, y0 + 88, cw, 19, 0xFF12142A);
        txt(g, textRenderer.trimToWidth("Filter: " + (w.match.isEmpty() ? "any item the search returns" : w.match), cw - 12), cx + 8, y0 + 93, 0xFF8A90B8);

        fieldRow(g, mx, my, cx, y0 + 108, cw, 20, "Search term (/ah ...)");
        fieldRow(g, mx, my, cx, y0 + 130, cw, 20, "Alert below ($)");
        if (w.alertBelow == 0) txt(g, "off", cx + cw - 80, y0 + 136, 0xFF5A6088);

        int by = y0 + 158;
        smallButton(g, mx, my, cx, by, 140, 18, "+ Add item in hand", true, () -> addHeld(c));
        button(g, mx, my, cx + cw - 100, by, 100, 18, "Remove",
                n > 1 ? 0xFFEF5358 : 0xFF3A3F5C, n > 1 ? 0xFF9E2B30 : 0xFF262A44, () -> {
                    if (n > 1) {
                        list.remove(watchSel);
                        watchSel = 0;
                        c.save();
                        reopen();
                    }
                });
        boolean showNote = System.currentTimeMillis() < noteUntil;
        txt(g, showNote ? note : "Each item is scanned in turn while Scout is running.", cx, y0 + 184,
                showNote ? 0xFFFFD84D : 0xFF6C7298);
    }

    private void addHeld(Config c) {
        if (client == null || client.player == null) return;
        ItemStack held = client.player.getMainHandStack();
        if (held.isEmpty()) {
            say("Hold the item first (close menu, hold it, reopen).");
            return;
        }
        Identifier id = Registries.ITEM.getId(held.getItem());
        Config.Watch nw = new Config.Watch();
        nw.match = id.toString();
        nw.name = held.getItem().getName().getString();
        nw.search = id.getPath().replace('_', ' ');
        for (int i = 0; i < c.watch.size(); i++) {
            if (c.watch.get(i).match.equals(nw.match)) {
                watchSel = i;
                say(nw.name + " is already on the list.");
                reopen();
                return;
            }
        }
        c.watch.add(nw);
        watchSel = c.watch.size() - 1;
        c.save();
        say("Added " + nw.name);
        reopen();
    }

    // ---- deals

    private void renderDeals(DrawContext g, int mx, int my) {
        int y0 = top + 36;
        title(g, "Deals", y0);
        smallButton(g, mx, my, cx + cw - 50, y0 + 1, 50, 14, "Clear", false, () -> Deals.clear());

        fieldRow(g, mx, my, cx, y0 + 16, cw, 20, "Min discount (%)");
        fieldRow(g, mx, my, cx, y0 + 38, cw, 20, "Min deal profit ($)");

        txt(g, "TIME", cx + 4, y0 + 64, acc());
        txt(g, "ITEM", cx + 62, y0 + 64, acc());
        txt(g, "PRICE", cx + 106, y0 + 64, acc());
        txt(g, "DISC", cx + 154, y0 + 64, acc());
        txt(g, "RESELL", cx + 184, y0 + 64, acc());
        txtRight(g, "PROFIT", cx + cw - 4, y0 + 64, acc());
        g.fill(cx, y0 + 75, cx + cw, y0 + 76, 0xFF2B2F52);

        List<Deals.Entry> list = Deals.entries();
        if (list.isEmpty()) {
            txt(g, "No deals yet. Listings far below market show up here.", cx + 4, y0 + 88, 0xFF8A90B8);
            txt(g, "Tip: the Overlay also highlights them in the auction.", cx + 4, y0 + 100, 0xFF6C7298);
        }
        for (int i = 0; i < 9; i++) {
            int idx = list.size() - 1 - i;
            if (idx < 0) break;
            Deals.Entry e = list.get(idx);
            int y = y0 + 79 + i * 13;
            if (i % 2 == 0) g.fill(cx, y - 1, cx + cw, y + 11, 0xFF161930);
            txt(g, Fmt.shortDate(e.time), cx + 4, y + 1, 0xFFDDE0F5);
            txt(g, textRenderer.trimToWidth(e.item, 40), cx + 62, y + 1, 0xFFB7C6F5);
            txt(g, Fmt.compact(e.price), cx + 106, y + 1, 0xFFFFFFFF);
            txt(g, e.discount + "%", cx + 154, y + 1, 0xFFFFD84D);
            txt(g, Fmt.compact(e.resell), cx + 184, y + 1, 0xFFB7C6F5);
            txtRight(g, Fmt.compactSigned(e.potential), cx + cw - 4, y + 1, 0xFF4DE08A);
        }
    }

    // ---- prices

    private void renderPrices(DrawContext g, int mx, int my) {
        Config c = Config.INSTANCE;
        int y0 = top + 36;
        title(g, "Prices", y0);
        List<Config.Watch> items = c.watch;
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
            txt(g, Fmt.shortDate(pts.get(0).t), cx, y0 + 190, 0xFF6C7298);
            txtRight(g, Fmt.shortDate(pts.get(pts.size() - 1).t), cx + cw, y0 + 190, 0xFF6C7298);
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
            txt(g, "No price data yet. Run Scout to collect prices.", x + 8, y + h / 2 - 4, 0xFF6C7298);
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

        int a = acc();
        int j = 0, prevPy = -1, lastPy = bot;
        for (int col = 0; col < iw; col++) {
            long t = tmin + (tmax - tmin) * col / Math.max(1, iw - 1);
            while (j < pts.size() - 2 && pts.get(j + 1).t <= t) j++;
            double v = pts.size() == 1 ? pts.get(0).p : interp(pts, j, t);
            int py = bot - (int) Math.round((v - vmin) / (double) (vmax - vmin) * ih);
            g.fillGradient(ix + col, py, ix + col + 1, bot, alpha(a, 70), alpha(a, 0));
            int lo2 = prevPy < 0 ? py : Math.min(py, prevPy);
            int hi2 = (prevPy < 0 ? py : Math.max(py, prevPy)) + 2;
            g.fill(ix + col, lo2, ix + col + 1, hi2, shade(a, 60));
            prevPy = py;
            lastPy = py;
        }

        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        int mxp = ix + iw - 1;
        g.fill(mxp - 3, lastPy - 2, mxp + 2, lastPy + 4, alpha(a, 40 + Math.round(pulse * 80)));
        g.fill(mxp - 1, lastPy, mxp + 1, lastPy + 2, 0xFFFFFFFF);

        txt(g, Fmt.compact(realMax), ix + 2, y + 4, 0xFF8A90B8);
        txt(g, Fmt.compact(realMin), ix + 2, bot - 10, 0xFF8A90B8);

        if (in(mx, my, ix, y, iw, h)) {
            int col = mx - ix;
            long t = tmin + (tmax - tmin) * col / Math.max(1, iw - 1);
            int k = 0;
            while (k < pts.size() - 2 && pts.get(k + 1).t <= t) k++;
            double v = pts.size() == 1 ? pts.get(0).p : interp(pts, k, t);
            g.fill(mx, ytop, mx + 1, bot, 0x88FFFFFF);
            txtRight(g, Fmt.shortDate(t) + "  " + Fmt.compact(Math.round(v)), x + w - 6, y + 4, 0xFFFFFFFF);
        } else {
            txtRight(g, pts.size() + " points", x + w - 6, y + 4, 0xFF6C7298);
        }
    }

    // ---- profit (flip tracking)

    private void renderProfit(DrawContext g, int mx, int my) {
        int y0 = top + 36;
        title(g, "Profit", y0);
        int bx = cx + cw - 3 * 44 - 4;
        for (int i = 0; i < VIEWS.length; i++) {
            final int idx = i;
            smallButton(g, mx, my, bx + i * 46, y0 + 1, 44, 14, VIEWS[i], profitView == i, () -> { profitView = idx; page = 0; reopen(); });
        }
        if (profitView == 1) renderFlipHistory(g, mx, my, y0);
        else if (profitView == 2) renderFlipAdd(g, mx, my, y0);
        else renderFlipSummary(g, mx, my, y0);
    }

    private void renderFlipSummary(DrawContext g, int mx, int my, int y0) {
        long total = animNum("profitTotal", Flips.total());
        txt(g, "TOTAL FLIP PROFIT", cx, y0 + 22, 0xFF8A90B8);
        big(g, Fmt.signed(total), cx, y0 + 33, 2f, profitColor(total));

        int y = y0 + 58;
        int cwid = (cw - 8) / 3;
        long today = Flips.since(Flips.startOfToday());
        card(g, cx, y, cwid, 34, "TODAY", Fmt.compactSigned(today), profitColor(today));
        card(g, cx + cwid + 4, y, cwid, 34, "SESSION", Fmt.compactSigned(Flips.session()), profitColor(Flips.session()));
        card(g, cx + 2 * (cwid + 4), y, cwid, 34, "PER HOUR", Fmt.compactSigned(Flips.perHour()), profitColor(Flips.perHour()));

        txt(g, "Flips: " + Flips.count() + "   Open buys: " + Flips.openCount() + "   Best: " + Fmt.compactSigned(Flips.best()),
                cx, y + 40, 0xFF8A90B8);

        int chartY = y + 54, chartH = 52;
        rrect(g, cx, chartY, cw, chartH, 0xFF12142A);
        long[] daily = Flips.daily(7);
        long lo = 0, hi = 0;
        for (long v : daily) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        if (hi == lo) hi = lo + 1;
        float grow = ease(Math.min(1f, (System.currentTimeMillis() - (openAt == 0 ? tabSwitchAt : openAt)) / 600f));
        float scale = (chartH - 8) / (float) (hi - lo);
        int baseY = chartY + 4 + Math.round(hi * scale);
        int slot = (cw - 8) / 7;
        for (int i = 0; i < 7; i++) {
            int bx = cx + 4 + i * slot + 4;
            int bw = slot - 8;
            int hgt = Math.round(Math.abs(daily[i]) * scale * grow);
            if (daily[i] >= 0) g.fillGradient(bx, baseY - hgt, bx + bw, baseY, 0xFF4DE08A, 0xFF1C8A50);
            else g.fillGradient(bx, baseY, bx + bw, baseY + hgt, 0xFFFF5D62, 0xFF9E2B30);
            String lbl = Fmt.dayLabel(6 - i);
            txt(g, lbl, bx + (bw - textRenderer.getWidth(lbl)) / 2, chartY + chartH + 3, i == 6 ? 0xFFFFFFFF : 0xFF6C7298);
        }
        g.fill(cx + 2, baseY, cx + cw - 2, baseY + 1, 0xFF3A3F5C);

        button(g, mx, my, cx + cw - 110, top + H - 22, 110, 14,
                resetArmed ? "Click to CONFIRM" : "Reset all data",
                resetArmed ? 0xFFEF5358 : 0xFF3A3F5C, resetArmed ? 0xFF9E2B30 : 0xFF262A44, () -> {
                    if (!resetArmed) resetArmed = true;
                    else { Flips.clear(); resetArmed = false; }
                });
        if (!Flips.lastNote.isEmpty()) txt(g, textRenderer.trimToWidth(Flips.lastNote, cw - 118), cx, top + H - 20, 0xFF6C7298);
    }

    private void renderFlipHistory(DrawContext g, int mx, int my, int y0) {
        txt(g, "DATE", cx + 4, y0 + 22, acc());
        txt(g, "ITEM", cx + 100, y0 + 22, acc());
        txt(g, "BOUGHT", cx + 146, y0 + 22, acc());
        txt(g, "SOLD", cx + 192, y0 + 22, acc());
        txtRight(g, "PROFIT", cx + cw - 4, y0 + 22, acc());
        g.fill(cx, y0 + 33, cx + cw, y0 + 34, 0xFF2B2F52);

        List<Flips.Entry> list = Flips.entries();
        int rowsPer = 11;
        if (list.isEmpty()) txt(g, "No flips yet. Add one in the Add view or via chat tracking.", cx + 4, y0 + 44, 0xFF8A90B8);
        for (int i = 0; i < rowsPer; i++) {
            int idx = list.size() - 1 - (page * rowsPer + i);
            if (idx < 0) break;
            Flips.Entry e = list.get(idx);
            int y = y0 + 36 + i * 13;
            if (i % 2 == 0) g.fill(cx, y - 1, cx + cw, y + 11, 0xFF161930);
            txt(g, Fmt.shortDate(e.time), cx + 4, y + 1, 0xFFDDE0F5);
            txt(g, textRenderer.trimToWidth(e.item, 42), cx + 100, y + 1, 0xFFB7C6F5);
            txt(g, Fmt.compact(e.buy), cx + 146, y + 1, 0xFFFFFFFF);
            txt(g, Fmt.compact(e.sell), cx + 192, y + 1, 0xFFFFFFFF);
            txtRight(g, Fmt.compactSigned(e.profit), cx + cw - 4, y + 1, profitColor(e.profit));
        }
        int pages = Math.max(1, (list.size() + rowsPer - 1) / rowsPer);
        page = Math.min(page, pages - 1);
        int by = top + H - 24;
        smallButton(g, mx, my, cx, by, 60, 16, "< Newer", page > 0, () -> { if (page > 0) page--; });
        smallButton(g, mx, my, cx + cw - 60, by, 60, 16, "Older >", page < pages - 1, () -> { if (page < pages - 1) page++; });
        g.drawCenteredTextWithShadow(textRenderer, Text.literal("Page " + (page + 1) + "/" + pages), cx + cw / 2, by + 4, 0xFF8A90B8);
    }

    private void renderFlipAdd(DrawContext g, int mx, int my, int y0) {
        txt(g, "Log a flip by hand", cx, y0 + 22, 0xFF8A90B8);
        fieldRow(g, mx, my, cx, y0 + 40, cw, 20, "Item");
        fieldRow(g, mx, my, cx, y0 + 61, cw, 20, "Bought for ($)");
        fieldRow(g, mx, my, cx, y0 + 82, cw, 20, "Sold for ($)");
        smallButton(g, mx, my, cx, y0 + 112, cw, 22, "Add flip", true, () -> {
            long b = num(manBuy, -1), s = num(manSell, -1);
            if (b < 0 || s < 0 || manBuy.isEmpty() || manSell.isEmpty()) { say("Fill in both prices."); return; }
            Flips.Entry e = Flips.addManual(manItem, b, s);
            manItem = "";
            manBuy = "";
            manSell = "";
            say("Added: " + e.item + " " + Fmt.signed(e.profit));
            reopen();
        });
        boolean showNote = System.currentTimeMillis() < noteUntil;
        txt(g, showNote ? note : "Profit = sold (minus sale tax) - bought.", cx, y0 + 142, showNote ? 0xFFFFD84D : 0xFF6C7298);
        txt(g, "Sale tax is set in Settings.", cx, y0 + 154, 0xFF6C7298);
    }

    // ---- chat tracking

    private void renderChat(DrawContext g, int mx, int my) {
        Config c = Config.INSTANCE;
        int y0 = top + 36;
        title(g, "Chat", y0);
        toggleRow(g, mx, my, cx, y0 + 16, cw, "Track buys/sales from chat", c.trackChat, () -> { c.trackChat = !c.trackChat; c.save(); });

        Chat.compile();
        txt(g, "Buy pattern (regex, needs a 'price' group)", cx, y0 + 41, 0xFFE6E8FF);
        if (!Chat.buyError.isEmpty()) txtRight(g, Chat.buyError, cx + cw, y0 + 41, 0xFFFF5D62);
        txt(g, "Sell pattern (regex, needs a 'price' group)", cx, y0 + 75, 0xFFE6E8FF);
        if (!Chat.sellError.isEmpty()) txtRight(g, Chat.sellError, cx + cw, y0 + 75, 0xFFFF5D62);

        txt(g, "Recent chat lines with $  (click to copy)", cx, y0 + 108, acc());
        g.fill(cx, y0 + 119, cx + cw, y0 + 120, 0xFF2B2F52);
        int i = 0;
        for (String line : new ArrayList<>(Chat.MONEY)) {
            if (i >= 6) break;
            int y = y0 + 123 + i * 12;
            boolean hov = in(mx, my, cx, y - 1, cw, 12);
            g.fill(cx, y - 1, cx + cw, y + 11, hov ? 0xFF22264A : (i % 2 == 0 ? 0xFF161930 : 0x00000000));
            txt(g, textRenderer.trimToWidth(line, cw - 8), cx + 4, y + 1, 0xFFDDE0F5);
            final String copy = line;
            hits.add(new Hit(cx, y - 1, cw, 12, () -> {
                if (client != null) client.keyboard.setClipboard(copy);
                say("Copied to clipboard.");
            }));
            i++;
        }
        if (Chat.MONEY.isEmpty()) txt(g, "Nothing yet. Buy or sell something and it shows up here.", cx + 4, y0 + 125, 0xFF8A90B8);
        boolean showNote = System.currentTimeMillis() < noteUntil;
        txt(g, showNote ? note : "Paste a line to me and I'll write the exact pattern.", cx, y0 + 198, showNote ? 0xFFFFD84D : 0xFF6C7298);
    }

    // ---- settings

    private void renderSettings(DrawContext g, int mx, int my) {
        Config c = Config.INSTANCE;
        int y0 = top + 36;
        title(g, "Settings", y0);
        int y = y0 + 16;
        int hw = (cw - 4) / 2;
        toggleRow(g, mx, my, cx, y, hw, "Overlay", c.overlay, () -> { c.overlay = !c.overlay; c.save(); });
        toggleRow(g, mx, my, cx + hw + 4, y, hw, "Price labels", c.labels, () -> { c.labels = !c.labels; c.save(); });
        toggleRow(g, mx, my, cx, y + 22, hw, "Auto sort", c.autoSort, () -> { c.autoSort = !c.autoSort; c.save(); });
        toggleRow(g, mx, my, cx + hw + 4, y + 22, hw, "Per-item", c.perItem, () -> { c.perItem = !c.perItem; c.save(); });

        String[] labels = { "Scan cooldown (s)", "Scan delay (ms)", "Auction timeout (s)", "Max pages to read", "Sale tax (%)", "Resell undercut ($)" };
        int base = y + 2 * 22 + 4;
        for (int i = 0; i < labels.length; i++) fieldRow(g, mx, my, cx, base + i * 20, cw, 19, labels[i]);
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
        button(g, mx, my, cx, y0 + 126, cw, 22, "Send test alert", acc(), shade(acc(), -75), () -> {
            c.save();
            Alerts.test(client);
        });
        txt(g, "Alerts fire for deals, price targets and finished flips.", cx, y0 + 156, 0xFF6C7298);
    }
}
