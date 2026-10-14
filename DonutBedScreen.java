package com.donutbed;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import java.util.function.Predicate;

public class DonutBedScreen extends Screen {
    private static final int W = 420;
    private static final int H = 256;
    private static final int HEAD = 34;
    private static final int SIDE = 88;

    // palette (dark purple + neon)
    private static final int ROW = 0xFF1E1236, ROW_H = 0xFF2A1A4C, BORDER = 0xFF3B2562, BOX = 0xFF120A24;
    private static final int TXT = 0xFFF2E9FF, MUTED = 0xFF9C8DBD, DIM = 0xFF6B5C8C;
    private static final int GREEN = 0xFF3DDC84, RED = 0xFFFF4D6D, GOLD = 0xFFFFC845, CYAN = 0xFF4DD8FF, PINK = 0xFFFF6EB4;

    private static final int[] THEME = { 0xFFA855F7, 0xFF4C8DFF, 0xFF35D07F, 0xFFFF9F43, 0xFFFF6EB4, 0xFFFF5D62, 0xFF2FD6D6 };
    private static final String[] THEME_NAMES = { "Donut", "Ocean", "Mint", "Sunset", "Bubblegum", "Ruby", "Aqua" };

    // tabs: 0 dashboard, 1 settings, 2 items, 3 prices, 4 deals, 5 alerts, 6 profit, 7 history
    private static final String[] TABS = { "Dashboard", "Settings", "Items", "Prices", "Deals", "Alerts", "Profit", "History" };
    private static final String[] RANGES = { "1H", "1D", "7D", "ALL" };
    private static final long[] RANGE_MS = { 3_600_000L, 86_400_000L, 7L * 86_400_000L, 0L };
    private static final String[] PERIODS = { "Today", "7 Days", "All Time" };
    private static final int[] PER_PAGE = { 5, 8, 10 };
    private static final String[] SPEEDS = { "Normal", "Fast", "Turbo" };

    private static final String[][] ICONS = {
            { "###.###.", "###.###.", "###.###.", "........", "###.###.", "###.###.", "###.###.", "........" },
            { "..#.....", "########", "..#.....", "......#.", "########", "......#.", "...#....", "########" },
            { "..####..", ".#.##.#.", "#..##..#", "########", "#......#", "#......#", "#......#", "########" },
            { "#.......", "#.....#.", "#..#.#.#", "#.#.#..#", "##.#....", "#.......", "#.......", "########" },
            { "....##..", "...##...", "..##....", ".######.", "...##...", "..##....", ".##.....", ".#......" },
            { "...##...", "..####..", "..####..", ".######.", ".######.", "########", "........", "...##..." },
            { "........", "......#.", "..#...#.", "..#.#.#.", "..#.#.#.", "#.#.#.#.", "#.#.#.#.", "########" },
            { "..####..", ".#.##.#.", "#..##..#", "#..###.#", "#......#", ".#....#.", "..####..", "........" }
    };
    private static final String[] DONUT = {
            "...####...", "..######..", ".###..###.", "###....###", "##......##",
            "##......##", "###....###", ".###..###.", "..######..", "...####..." };
    private static final String[] I_CAL = { "########", "#######.", "#......#", "#.#.#..#", "#......#", "#.#.#..#", "#......#", "########" };
    private static final String[] I_TROPHY = { "########", ".######.", ".######.", "..####..", "...##...", "...##...", "..####..", ".######." };
    private static final String[] I_TAG = { "..####..", ".######.", "########", "########", "########", ".######.", "..####..", "...##..." };
    private static final String[] I_KEY = { "..###...", ".#...#..", ".#...#..", "..###...", "...#....", "...##...", "...#....", "...##..." };
    private static final String[] I_DOWN = { "...##...", "...##...", "...##...", "...##...", "#######.", ".#####..", "..###...", "...#...." };
    private static final String[] I_COPY = { "..#####.", "..#...#.", "#####.#.", "#...#.#.", "#...###.", "#...#...", "#...#...", "#####..." };
    private static final String[] I_SEARCH = { "..####..", ".#....#.", ".#....#.", ".#....#.", "..####..", ".....##.", "......##", ".......#" };
    private static final String[] I_LINK = { "..###...", ".#...#..", ".#......", "..####..", "....#...", "...#..#.", "....###." , "........" };

    private static final Map<String, String> TIPS = new HashMap<>();
    static {
        TIPS.put("Auto Sell", "Off = watch prices only, never list anything.");
        TIPS.put("Auto-pick item from inventory", "Moves the item from your inventory into your hand.");
        TIPS.put("Auto-pick", "Moves the item from your inventory into your hand.");
        TIPS.put("Outlier Guard", "Ignores lowball listings far under the rest.");
        TIPS.put("Selling speed", "Turbo lists fastest but can trip server anti-spam.");
        TIPS.put("Price cache (s)", "Fast/Turbo: reuse the last scanned price this long.");
        TIPS.put("Default undercut ($)", "How many $ below the cheapest price you list at.");
        TIPS.put("Percent undercut", "Undercut by a % of the price instead of flat $.");
        TIPS.put("Percent undercut (%)", "Used when Percent undercut is on.");
        TIPS.put("Default minimum price ($)", "Never list below this price.");
        TIPS.put("Min profit per item ($)", "Never list below your cost + this.");
        TIPS.put("Outlier protection", "Ignore lowball listings far under the rest.");
        TIPS.put("Lowball threshold (%)", "A listing this % under the next one is ignored.");
        TIPS.put("Auto sort (lowest first)", "Clicks the sort button until prices run lowest-first.");
        TIPS.put("Per-item pricing (stacks)", "Stack price divided by stack size.");
        TIPS.put("Max pages to read", "Pages to read when it can't sort.");
        TIPS.put("Scan delay (ms)", "Wait after the GUI opens before reading it.");
        TIPS.put("Auction timeout (s)", "Give up waiting for the GUI after this.");
        TIPS.put("Cooldown (s)", "Minimum pause between auction cycles.");
        TIPS.put("Debug messages in chat", "Shows exactly what price it read, every round.");
        TIPS.put("Deal finder", "Alerts you to listings far below market.");
        TIPS.put("Min discount (%)", "How far under market counts as a deal.");
        TIPS.put("Enabled (rotates in)", "Include this item in the selling rotation.");
        TIPS.put("Cost per item ($)", "What you paid. Used to work out profit.");
        TIPS.put("Minimum price ($)", "Never list this item below this price.");
        TIPS.put("Undercut ($)", "Overrides the default undercut for this item.");
        TIPS.put("Chat notifications", "Messages in chat when something happens.");
        TIPS.put("Sound alert", "Plays a sound when you list or find a deal.");
        TIPS.put("Popup toast", "Shows a popup in the corner.");
    }

    private static int tab = 0;
    private static int histPage = 0;
    private static int perPageSel = 2;
    private static String histSearch = "";
    private static int itemSel = 0;
    private static int priceSel = 0;
    private static int priceRange = 1;
    private static int profitPeriod = 0;
    private static boolean collapsed = false;
    private static float scrollTarget = 0f, scrollCur = 0f, maxScroll = 0f;
    private static float tabHiY = -1f;
    private static long tabSwitchAt = 0;
    private static String note = "";
    private static long noteUntil = 0;
    private static final Map<String, Float> ANIM = new HashMap<>();
    private static final Map<String, Double> NUMS = new HashMap<>();

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private final long openAt;
    private TextFieldWidget editField;
    private String editKey = null;
    private boolean editFound = false;
    private int clipTop = Integer.MIN_VALUE, clipBot = Integer.MAX_VALUE;
    private boolean resetArmed = false;
    private String tip = null;
    private int left, top, cx, cw, y0;

    public DonutBedScreen() {
        super(Text.literal("Donut Bed Client"));
        openAt = System.currentTimeMillis();
    }

    private static void say(String s) {
        note = s;
        noteUntil = System.currentTimeMillis() + 3500;
    }

    private static Config.ItemCfg selItem() {
        List<Config.ItemCfg> items = Config.INSTANCE.items;
        itemSel = Math.max(0, Math.min(itemSel, items.size() - 1));
        return items.get(itemSel);
    }

    private static Config.ItemCfg cfgByName(String name) {
        for (Config.ItemCfg it : Config.INSTANCE.items) if (it.name.equals(name)) return it;
        return null;
    }

    private static ItemStack iconStack(Config.ItemCfg it, String fallbackName) {
        if (it == null) {
            return new ItemStack(fallbackName != null && fallbackName.toLowerCase().contains("bed") ? Items.RED_BED : Items.PAPER);
        }
        if ("@bed".equals(it.match)) {
            if (it.colorMask != 0) {
                for (int i = 0; i < 16; i++) {
                    if (((it.colorMask >> i) & 1) != 0) {
                        return new ItemStack(Registries.ITEM.get(Identifier.of("minecraft", Config.BED_COLORS[i] + "_bed")));
                    }
                }
            }
            return new ItemStack(Items.RED_BED);
        }
        Identifier id = Identifier.tryParse(it.match);
        if (id == null) return new ItemStack(Items.PAPER);
        return new ItemStack(Registries.ITEM.get(id));
    }

    private static int acc() {
        return THEME[Math.max(0, Math.min(THEME.length - 1, Config.INSTANCE.theme))];
    }

    // ------------------------------------------------------------ setup / editing

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(6, (height - H) / 2);
        cx = left + SIDE + 10;
        cw = W - SIDE - 20;
        y0 = top + HEAD + 6;
        editField = new TextFieldWidget(textRenderer, 0, 0, 60, 10, Text.empty());
        editField.setDrawsBackground(false);
        editField.visible = false;
        addDrawableChild(editField);
        editKey = null;
    }

    private void startEdit(String key, int kind, int max, String initial, Consumer<String> onChange) {
        Predicate<String> pred = kind == 0 ? (s -> s.matches("\\d*"))
                : kind == 1 ? (s -> s.matches("\\d{0,2}(\\.\\d{0,2})?")) : (s -> true);
        editField.setChangedListener(s -> {});
        editField.setMaxLength(max);
        editField.setTextPredicate(pred);
        editField.setText(initial);
        editField.setChangedListener(onChange);
        editField.visible = true;
        editKey = key;
        setFocused(editField);
    }

    private void stopEdit() {
        editKey = null;
        if (editField != null) editField.visible = false;
        setFocused(null);
        Config.INSTANCE.sanitize();
        Config.INSTANCE.save();
    }

    @Override
    public void removed() {
        Config.INSTANCE.sanitize();
        Config.INSTANCE.save();
    }

    @Override
    public boolean shouldPause() { return false; }

    private void goTab(int t) {
        stopEdit();
        tab = t;
        histPage = 0;
        scrollTarget = 0;
        scrollCur = 0;
        tabSwitchAt = System.currentTimeMillis();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editKey != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                stopEdit();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (DonutBedClient.openKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + TABS.length) {
            goTab(keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        if (tab == 1 && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget + (keyCode == GLFW.GLFW_KEY_DOWN ? 24 : -24)));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        if (!collapsed && tab == 1) {
            scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - (float) va * 20f));
            return true;
        }
        return super.mouseScrolled(mx, my, ha, va);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int imx = (int) mx, imy = (int) my;
        if (editKey != null && editField.visible) {
            boolean inside = imx >= editField.getX() - 5 && imx < editField.getX() + editField.getWidth() + 5
                    && imy >= editField.getY() - 4 && imy < editField.getY() + 13;
            if (!inside) stopEdit();
        }
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

    // ------------------------------------------------------------ small helpers

    private boolean hover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipTop && my < clipBot;
    }

    private void addHit(int x, int y, int w, int h, Runnable r) {
        int a = Math.max(y, clipTop), b = Math.min(y + h, clipBot);
        if (b <= a) return;
        hits.add(new Hit(x, a, w, b - a, r));
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

    private static void panel(DrawContext g, int x, int y, int w, int h, int fill, int border) {
        rrect(g, x, y, w, h, border);
        rrect(g, x + 1, y + 1, w - 2, h - 2, fill);
    }

    private void bitmap(DrawContext g, String[] rows, int x, int y, int color, int scale) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int k = 0; k < row.length(); k++) {
                if (row.charAt(k) == '#') g.fill(x + k * scale, y + r * scale, x + (k + 1) * scale, y + (r + 1) * scale, color);
            }
        }
    }

    private void txt(DrawContext g, String s, int x, int y, int color) {
        g.drawTextWithShadow(textRenderer, Text.literal(s), x, y, color);
    }

    private void txtRight(DrawContext g, String s, int rightX, int y, int color) {
        txt(g, s, rightX - textRenderer.getWidth(s), y, color);
    }

    private void txtCenter(DrawContext g, String s, int centerX, int y, int color) {
        txt(g, s, centerX - textRenderer.getWidth(s) / 2, y, color);
    }

    private void big(DrawContext g, String s, int x, int y, float sc, int color) {
        var m = g.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(sc, sc, 1f);
        g.drawTextWithShadow(textRenderer, Text.literal(s), 0, 0, color);
        m.pop();
    }

    private void icon(DrawContext g, ItemStack st, int x, int y, float scale) {
        var m = g.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1f);
        g.drawItem(st, 0, 0);
        m.pop();
    }

    private void gradButton(DrawContext g, int mx, int my, int x, int y, int w, int h, String label,
                            int c1, int c2, Runnable action) {
        boolean hov = hover(mx, my, x, y, w, h);
        float hv = anim("gb" + x + "_" + y, hov ? 1f : 0f, 0.3f);
        int d = Math.round(hv * 30);
        rrect(g, x, y, w, h, shade(c2, -50 + d));
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, shade(c1, d), shade(c2, d));
        g.fill(x + 2, y + 1, x + w - 2, y + 2, alpha(0xFFFFFF, 40 + Math.round(hv * 50)));
        g.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + w / 2, y + (h - 8) / 2 + 1, 0xFFFFFFFF);
        addHit(x, y, w, h, action);
    }

    private void primary(DrawContext g, int mx, int my, int x, int y, int w, int h, String label, Runnable a) {
        gradButton(g, mx, my, x, y, w, h, label, shade(acc(), 30), shade(acc(), -50), a);
    }

    private void outline(DrawContext g, int mx, int my, int x, int y, int w, int h, String label, boolean active, Runnable a) {
        boolean hov = hover(mx, my, x, y, w, h);
        float hv = anim("ob" + x + "_" + y, hov ? 1f : 0f, 0.3f);
        panel(g, x, y, w, h, active ? alpha(acc(), 255) & 0xFF000000 | (shade(acc(), -85) & 0xFFFFFF) : lerp(ROW, ROW_H, hv),
                active ? acc() : lerp(BORDER, acc(), hv * 0.7f));
        g.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + w / 2, y + (h - 8) / 2 + 1, active ? 0xFFFFFFFF : TXT);
        addHit(x, y, w, h, a);
    }

    private boolean rowBg(DrawContext g, int mx, int my, int x, int y, int w, int h, String label) {
        boolean hov = hover(mx, my, x, y, w, h);
        panel(g, x, y, w, h, hov ? ROW_H : ROW, hov ? lerp(BORDER, acc(), 0.5f) : BORDER);
        if (label != null) txt(g, label, x + 8, y + (h - 8) / 2 + 1, TXT);
        if (hov && label != null && TIPS.containsKey(label)) tip = TIPS.get(label);
        return hov;
    }

    private void drawSwitch(DrawContext g, int x, int y, String key, boolean val) {
        float cur = anim("sw" + key, val ? 1f : 0f, 0.25f);
        rrect(g, x, y, 24, 11, lerp(0xFF3A2D57, GREEN, cur));
        rrect(g, x + 2 + Math.round(cur * 13), y + 2, 7, 7, 0xFFFFFFFF);
    }

    /** A full-width (or custom width) toggle row. Returns nothing; use toggleList for list layout. */
    private void toggleAt(DrawContext g, int mx, int my, int x, int y, int w, int h, String label, boolean val, Runnable click) {
        rowBg(g, mx, my, x, y, w, h, label);
        drawSwitch(g, x + w - 31, y + (h - 11) / 2, label, val);
        addHit(x, y, w, h, click);
    }

    private int toggleRow(DrawContext g, int mx, int my, int y, String label, boolean val, Runnable click) {
        toggleAt(g, mx, my, cx, y, cw, 18, label, val, click);
        return y + 20;
    }

    private void stepBtn(DrawContext g, int mx, int my, int x, int y, String label, Runnable a) {
        boolean hov = hover(mx, my, x, y, 14, 14);
        panel(g, x, y, 14, 14, hov ? shade(acc(), -60) : BOX, hov ? acc() : BORDER);
        g.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + 7, y + 3, TXT);
        addHit(x, y, 14, 14, a);
    }

    private void valueBox(DrawContext g, int mx, int my, String key, int x, int y, int w, int h, String shown,
                          String placeholder, int kind, int max, String raw, Consumer<String> onChange, boolean right) {
        boolean editing = key.equals(editKey);
        boolean hov = hover(mx, my, x, y, w, h);
        panel(g, x, y, w, h, BOX, editing ? acc() : (hov ? lerp(BORDER, acc(), 0.5f) : BORDER));
        if (editing) {
            editField.setX(x + 5);
            editField.setY(y + (h - 8) / 2);
            editField.setWidth(w - 10);
            editFound = true;
        } else {
            String s = shown;
            int col = TXT;
            if (s == null || s.isEmpty()) { s = placeholder; col = DIM; }
            s = textRenderer.trimToWidth(s, w - 10);
            if (right) txtRight(g, s, x + w - 5, y + (h - 8) / 2 + 1, col);
            else txt(g, s, x + 5, y + (h - 8) / 2 + 1, col);
            addHit(x, y, w, h, () -> startEdit(key, kind, max, raw, onChange));
        }
    }

    private int numRowX(DrawContext g, int mx, int my, int y, String label, String key, String shown, String raw,
                        Runnable inc, Runnable dec, Consumer<String> onType, int h) {
        rowBg(g, mx, my, cx, y, cw, h, label);
        int bw = 70;
        int plusX = cx + cw - 20;
        int minusX = plusX - 16;
        int boxX = minusX - 4 - bw;
        valueBox(g, mx, my, key, boxX, y + (h - 14) / 2, bw, 14, shown, "", 0, 15, raw, onType, true);
        stepBtn(g, mx, my, minusX, y + (h - 14) / 2, "-", dec);
        stepBtn(g, mx, my, plusX, y + (h - 14) / 2, "+", inc);
        return y + h + 2;
    }

    private int numRow(DrawContext g, int mx, int my, int y, String label, String key, long val, long step,
                       long min, long max, Consumer<Long> set) {
        final long mult = hasShiftDown() ? 10 : 1;
        return numRowX(g, mx, my, y, label, key, String.format("%,d", val), String.valueOf(val),
                () -> set.accept(Math.min(max, val + step * mult)),
                () -> set.accept(Math.max(min, val - step * mult)),
                s -> { try { set.accept(Math.max(min, Math.min(max, Long.parseLong(s)))); } catch (Exception ignored) {} }, 18);
    }

    private int decRow(DrawContext g, int mx, int my, int y, String label, String key, double val, double step,
                       double min, double max, Consumer<Double> set) {
        rowBg(g, mx, my, cx, y, cw, 18, label);
        int bw = 70;
        int plusX = cx + cw - 20;
        int minusX = plusX - 16;
        int boxX = minusX - 4 - bw;
        valueBox(g, mx, my, key, boxX, y + 2, bw, 14, String.valueOf(val), "", 1, 6, String.valueOf(val),
                s -> { try { set.accept(Math.max(min, Math.min(max, Double.parseDouble(s)))); } catch (Exception ignored) {} }, true);
        stepBtn(g, mx, my, minusX, y + 2, "-", () -> set.accept(Math.max(min, Math.round((val - step) * 100.0) / 100.0)));
        stepBtn(g, mx, my, plusX, y + 2, "+", () -> set.accept(Math.min(max, Math.round((val + step) * 100.0) / 100.0)));
        return y + 20;
    }

    private int segRow(DrawContext g, int mx, int my, int y, String label, String[] opts, int sel, Consumer<Integer> set) {
        rowBg(g, mx, my, cx, y, cw, 18, label);
        int sw = 46;
        int sx = cx + cw - 6 - opts.length * (sw + 2) + 2;
        for (int i = 0; i < opts.length; i++) {
            final int idx = i;
            int x = sx + i * (sw + 2);
            boolean on = sel == i;
            boolean hov = hover(mx, my, x, y + 2, sw, 14);
            if (on) {
                rrect(g, x, y + 2, sw, 14, shade(acc(), -50));
                g.fillGradient(x + 1, y + 3, x + sw - 1, y + 15, shade(acc(), 30), shade(acc(), -50));
            } else {
                panel(g, x, y + 2, sw, 14, hov ? ROW_H : BOX, BORDER);
            }
            g.drawCenteredTextWithShadow(textRenderer, Text.literal(opts[i]), x + sw / 2, y + 5, on ? 0xFFFFFFFF : MUTED);
            addHit(x, y + 2, sw, 14, () -> set.accept(idx));
        }
        return y + 20;
    }

    private int section(DrawContext g, int y, String label) {
        txt(g, label, cx + 2, y + 3, acc());
        g.fill(cx + textRenderer.getWidth(label) + 8, y + 7, cx + cw - 2, y + 8, BORDER);
        return y + 14;
    }

    private void card(DrawContext g, int x, int y, int w, int h, String label, String value, int vcolor) {
        panel(g, x, y, w, h, ROW, BORDER);
        g.fillGradient(x + 1, y + 2, x + w - 1, y + 9, alpha(vcolor, 26), alpha(vcolor, 0));
        txt(g, label, x + 7, y + 5, MUTED);
        txt(g, textRenderer.trimToWidth(value, w - 12), x + 7, y + h - 13, vcolor);
    }

    private static String money(long v) { return v < 0 ? "-" : Profit.compact(v); }
    private static int profitColor(long v) { return v >= 0 ? GREEN : RED; }

    private static int statusColor(AutoSeller.Status s) {
        return switch (s) {
            case IDLE -> 0xFF9AA0C0;
            case SCANNING -> 0xFFFFD84D;
            case PRICE_FOUND -> GREEN;
            case SELLING -> CYAN;
            case ERROR -> RED;
        };
    }

    private static int pulsing(AutoSeller.Status s) {
        int base = s == AutoSeller.Status.IDLE ? GREEN : statusColor(s);
        if (s != AutoSeller.Status.IDLE) {
            float p = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 220.0);
            return lerp(base, 0xFF202438, p * 0.7f);
        }
        return base;
    }

    private void title(DrawContext g, String t, String sub) {
        big(g, t, cx, y0, 1.35f, 0xFFFFFFFF);
        txt(g, sub, cx, y0 + 15, MUTED);
        if (System.currentTimeMillis() < noteUntil) txtRight(g, note, cx + cw, y0 + 3, GOLD);
    }

    // ------------------------------------------------------------ frame

    @Override
    public void renderBackground(DrawContext g, int mx, int my, float delta) {
        // intentionally empty: render() draws our own backdrop and window
    }

    private int winH() { return collapsed ? HEAD : H; }

    private void drawBackdrop(DrawContext g, long t) {
        g.fillGradient(0, 0, width, height, 0xE4190A30, 0xF40A0414);
        for (int i = 0; i < 50; i++) {
            int sx = (int) ((i * 7919L) % Math.max(1, width));
            int sy = (int) ((i * 104729L) % Math.max(1, height - 30));
            float tw = 0.5f + 0.5f * (float) Math.sin(t / 600.0 + i);
            g.fill(sx, sy, sx + 1, sy + 1, alpha(i % 4 == 0 ? PINK : 0xFFFFFF, 30 + Math.round(tw * 110)));
        }
        for (int bx = 0; bx < width; bx += 10) {
            int hgt = 10 + (int) (Math.abs(((bx / 10) * 2654435761L) >> 5) % 34);
            g.fill(bx, height - hgt, bx + 10, height, 0xFF0A0516);
            if (hgt > 26) g.fill(bx + 2, height - hgt + 3, bx + 4, height - hgt + 5, alpha(PINK, 70));
        }
    }

    private void drawWindow(DrawContext g, long t) {
        int wh = winH();
        for (int i = 1; i <= 7; i++) {
            g.fill(left - i, top - i, left + W + i, top + wh + i, alpha(acc(), 9));
        }
        float glow = 0.5f + 0.5f * (float) Math.sin(t / 700.0);
        rrect(g, left - 1, top - 1, W + 2, wh + 2, lerp(shade(acc(), -70), shade(acc(), 10), glow));
        g.fillGradient(left, top, left + W, top + wh, 0xFF1B1033, 0xFF0E0820);
        if (!collapsed) {
            for (int i = 0; i < 26; i++) {
                int px = left + SIDE + 6 + (int) ((i * 61L) % (W - SIDE - 12)) + (int) Math.round(Math.sin(t / 900.0 + i) * 5);
                int span = H - HEAD - 10;
                int py = top + H - (int) (((t / (24 + (i % 7) * 9)) + i * 53L) % span);
                int[] cols = { PINK, 0xFFFFE066, CYAN, 0xFFFFFFFF };
                g.fill(px, py, px + 2, py + 1, alpha(cols[i % 4], 55));
            }
        }
    }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        hits.clear();
        tip = null;
        editFound = false;
        long now = System.currentTimeMillis();
        drawBackdrop(g, now);

        float p = Math.min(1f, (now - openAt) / 240f);
        boolean opening = p < 1f;
        var m = g.getMatrices();
        m.push();
        if (opening) {
            float s = 0.9f + 0.1f * ease(p);
            m.translate(width / 2f, height / 2f, 0);
            m.scale(s, s, 1f);
            m.translate(-width / 2f, -height / 2f, 0);
        }

        drawWindow(g, now);
        drawHeader(g, mx, my, now);
        if (!collapsed) {
            drawSidebar(g, mx, my, now);
            switch (tab) {
                case 0 -> renderDashboard(g, mx, my);
                case 1 -> renderSettings(g, mx, my);
                case 2 -> renderItems(g, mx, my);
                case 3 -> renderPrices(g, mx, my);
                case 4 -> renderDeals(g, mx, my);
                case 5 -> renderAlerts(g, mx, my);
                case 6 -> renderProfit(g, mx, my);
                default -> renderHistory(g, mx, my);
            }
        }
        if (editKey != null && !editFound) stopEdit();

        // the shared text editor is drawn last so it sits on top of its box
        super.render(g, mx, my, delta);

        float tf = Math.min(1f, (now - tabSwitchAt) / 180f);
        if (tf < 1f && !collapsed) {
            g.fill(cx - 4, top + HEAD + 1, left + W, top + H, (((int) ((1f - tf) * 235)) << 24) | 0x140B26);
        }
        if (opening) g.fill(left - 8, top - 8, left + W + 8, top + winH() + 8, (((int) ((1f - p) * 200)) << 24) | 0x05030C);
        m.pop();

        if (tip != null && !opening) {
            int tw = textRenderer.getWidth(tip) + 12;
            int tx = Math.min(mx + 10, width - tw - 4);
            int ty = Math.min(my + 12, height - 20);
            panel(g, tx, ty, tw, 16, 0xFF0E0820, acc());
            txt(g, tip, tx + 6, ty + 4, TXT);
        }
    }

    private void drawHeader(DrawContext g, int mx, int my, long t) {
        int a = acc();
        g.fillGradient(left, top, left + W, top + HEAD, shade(a, -85), 0xFF1B1033);
        g.fill(left, top + HEAD - 1, left + W, top + HEAD, lerp(shade(a, -40), a, 0.5f + 0.5f * (float) Math.sin(t / 500.0)));

        int bob = Math.round((float) Math.sin(t / 500.0) * 1f);
        bitmap(g, DONUT, left + 10, top + 7 + bob, PINK, 2);
        g.fill(left + 16, top + 13 + bob, left + 18, top + 15 + bob, 0xFFFFFFFF);
        g.fill(left + 24, top + 17 + bob, left + 26, top + 19 + bob, 0xFFFFE066);
        g.fill(left + 12, top + 19 + bob, left + 14, top + 21 + bob, CYAN);
        big(g, "DONUT BED CLIENT", left + 38, top + 6, 1.4f, 0xFFFFFFFF);
        txt(g, "auto-undercut seller", left + 38, top + 22, MUTED);

        // window buttons
        int bx = left + W - 8 - 3 * 18;
        for (int i = 0; i < 3; i++) {
            int x = bx + i * 18, y = top + 9;
            boolean hov = hover(mx, my, x, y, 15, 15);
            panel(g, x, y, 15, 15, hov ? (i == 2 ? 0xFF5A1530 : 0xFF2E1B52) : 0xFF160D2B, hov ? (i == 2 ? RED : a) : BORDER);
            int col = hov && i == 2 ? RED : TXT;
            if (i == 0) g.fill(x + 4, y + 10, x + 11, y + 11, col);
            else if (i == 1) {
                g.fill(x + 4, y + 4, x + 11, y + 5, col);
                g.fill(x + 4, y + 10, x + 11, y + 11, col);
                g.fill(x + 4, y + 4, x + 5, y + 11, col);
                g.fill(x + 10, y + 4, x + 11, y + 11, col);
            } else {
                for (int k = 0; k < 7; k++) {
                    g.fill(x + 4 + k, y + 4 + k, x + 5 + k, y + 5 + k, col);
                    g.fill(x + 10 - k, y + 4 + k, x + 11 - k, y + 5 + k, col);
                }
            }
            final int idx = i;
            hits.add(new Hit(x, y, 15, 15, () -> {
                if (idx == 0) collapsed = true;
                else if (idx == 1) collapsed = false;
                else close();
                stopEdit();
            }));
        }
        // connected pill
        boolean on = client != null && client.player != null && client.getNetworkHandler() != null;
        String ct = on ? "Connected" : "Offline";
        int pw = textRenderer.getWidth(ct) + 24;
        int px = bx - 8 - pw;
        panel(g, px, top + 8, pw, 17, 0xFF160D2B, on ? 0xFF2A8A58 : 0xFF8A2A40);
        rrect(g, px + 7, top + 14, 6, 6, on ? GREEN : RED);
        txt(g, ct, px + 17, top + 12, TXT);
    }

    private void drawSidebar(DrawContext g, int mx, int my, long t) {
        int a = acc();
        g.fill(left, top + HEAD, left + SIDE, top + H, 0xFF120A24);
        g.fill(left + SIDE, top + HEAD, left + SIDE + 1, top + H, BORDER);

        float target = top + HEAD + 8 + tab * 22;
        tabHiY = tabHiY < 0 ? target : tabHiY + (target - tabHiY) * 0.3f;
        int hy = Math.round(tabHiY);
        rrect(g, left + 5, hy, SIDE - 10, 19, shade(a, -70));
        g.fillGradient(left + 6, hy + 1, left + SIDE - 6, hy + 18, shade(a, 35), shade(a, -45));
        g.fill(left + 7, hy + 1, left + SIDE - 7, hy + 2, alpha(0xFFFFFF, 60));

        for (int i = 0; i < TABS.length; i++) {
            int x = left + 5, y = top + HEAD + 8 + i * 22, w = SIDE - 10;
            boolean sel = tab == i;
            boolean hov = hover(mx, my, x, y, w, 19);
            if (hov && !sel) panel(g, x, y, w, 19, 0xFF1A1033, BORDER);
            bitmap(g, ICONS[i], x + 8, y + 5, sel ? 0xFFFFFFFF : (hov ? 0xFFD8C8F8 : MUTED), 1);
            txt(g, TABS[i], x + 22, y + 6, sel ? 0xFFFFFFFF : (hov ? 0xFFEADFFF : MUTED));
            final int idx = i;
            hits.add(new Hit(x, y, w, 19, () -> goTab(idx)));
        }

        AutoSeller.Status st = AutoSeller.status;
        int fy = top + H - 24;
        g.fill(left + 8, fy - 5, left + SIDE - 8, fy - 4, BORDER);
        rrect(g, left + 9, fy + 2, 6, 6, pulsing(st));
        txt(g, st.name().replace('_', ' '), left + 19, fy, statusColor(st));
        txt(g, "v1.2", left + 9, fy + 11, DIM);
    }

    // ------------------------------------------------------------ dashboard

    private void renderDashboard(DrawContext g, int mx, int my) {
        Config c = Config.INSTANCE;
        AutoSeller.Status st = AutoSeller.status;
        title(g, "Dashboard", "Overview of your selling activity");

        int y = y0 + 26;
        int sc = statusColor(st);
        panel(g, cx, y, cw, 42, ROW, BORDER);
        g.fillGradient(cx + 1, y + 1, cx + cw - 1, y + 20, alpha(sc, 22), alpha(sc, 0));
        rrect(g, cx + 12, y + 11, 9, 9, pulsing(st));
        big(g, st.name().replace('_', ' '), cx + 28, y + 7, 1.5f, st == AutoSeller.Status.IDLE ? 0xFFFFFFFF : sc);
        String sub = st == AutoSeller.Status.IDLE ? (c.enabled ? "Waiting for next cycle..." : "Ready to start selling") : AutoSeller.message;
        txt(g, textRenderer.trimToWidth(sub, cw - 100), cx + 28, y + 26, MUTED);
        Config.ItemCfg cur = cfgByName(AutoSeller.currentName);
        if (cur == null) cur = c.items.get(0);
        icon(g, iconStack(cur, null), cx + cw - 42, y + 5, 2f);

        y += 46;
        boolean on = c.enabled;
        if (on) {
            float glow = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
            rrect(g, cx - 2, y - 2, cw + 4, 26, lerp(0xFF1B1033, 0xFF7A2A40, 0.4f + glow * 0.6f));
        }
        gradButton(g, mx, my, cx, y, cw, 22, on ? "STOP" : ">  START",
                on ? 0xFFFF6B85 : shade(acc(), 40), on ? 0xFFA0233F : shade(acc(), -45), () -> {
                    c.enabled = !c.enabled;
                    c.save();
                    if (c.enabled) close();
                });

        y += 26;
        int cwid = (cw - 8) / 3;
        card(g, cx, y, cwid, 30, "MARKET PRICE", money(AutoSeller.cheapest), 0xFFFFFFFF);
        card(g, cx + cwid + 4, y, cwid, 30, "SELLING AT", money(AutoSeller.sellPrice), CYAN);
        long total = animNum("dashTotal", Profit.total());
        card(g, cx + 2 * (cwid + 4), y, cwid, 30, "PROFIT (EST)", Profit.compactSigned(total), profitColor(total));

        y += 34;
        int tw = (cw - 8) / 3;
        toggleAt(g, mx, my, cx, y, tw, 18, "Auto Sell", c.autoSell, () -> { c.autoSell = !c.autoSell; c.save(); });
        toggleAt(g, mx, my, cx + tw + 4, y, tw, 18, "Auto-pick", c.autoPickBed, () -> { c.autoPickBed = !c.autoPickBed; c.save(); });
        toggleAt(g, mx, my, cx + 2 * (tw + 4), y, tw, 18, "Outlier Guard", c.outlierProtect, () -> { c.outlierProtect = !c.outlierProtect; c.save(); });

        y += 22;
        segRow(g, mx, my, y, "Selling speed", SPEEDS, c.speed, v -> { c.speed = v; c.save(); });

        y += 22;
        panel(g, cx, y, cw, 34, ROW, BORDER);
        int enabled = 0;
        for (Config.ItemCfg it : c.items) if (it.enabled) enabled++;
        int colW = cw / 2;
        bitmap(g, I_TAG, cx + 8, y + 5, acc(), 1);
        txt(g, "Items Rotating: " + enabled, cx + 22, y + 5, TXT);
        bitmap(g, I_DOWN, cx + colW + 4, y + 5, PINK, 1);
        txt(g, "Undercut: " + (c.undercutMode == 1 ? c.undercutPercent + "%" : Profit.money(c.undercut)), cx + colW + 18, y + 5, TXT);
        bitmap(g, I_TAG, cx + 8, y + 19, GOLD, 1);
        txt(g, "Min Price: " + Profit.money(c.minPrice), cx + 22, y + 19, TXT);
        bitmap(g, I_KEY, cx + colW + 4, y + 19, GOLD, 1);
        txt(g, "Menu Key: " + DonutBedClient.openKey.getBoundKeyLocalizedText().getString(), cx + colW + 18, y + 19, TXT);
    }

    // ------------------------------------------------------------ settings (scrollable)

    private void renderSettings(DrawContext g, int mx, int my) {
        Config c = Config.INSTANCE;
        title(g, "Settings", "Configure automation and filters");

        int vTop = y0 + 26, vBot = top + H - 28;
        clipTop = vTop;
        clipBot = vBot;
        g.enableScissor(cx - 4, vTop, cx + cw + 6, vBot);
        int startY = vTop - Math.round(scrollCur);
        int y = startY;

        y = section(g, y, "SELLING");
        y = toggleRow(g, mx, my, y, "Auto Sell", c.autoSell, () -> { c.autoSell = !c.autoSell; c.save(); });
        y = toggleRow(g, mx, my, y, "Auto-pick item from inventory", c.autoPickBed, () -> { c.autoPickBed = !c.autoPickBed; c.save(); });
        y = segRow(g, mx, my, y, "Selling speed", SPEEDS, c.speed, v -> { c.speed = v; c.save(); });
        y = numRow(g, mx, my, y, "Price cache (s)", "s.cache", c.priceCacheSec, 5, 0, 300, v -> c.priceCacheSec = (int) (long) v);
        y = numRow(g, mx, my, y, "Default undercut ($)", "s.undercut", c.undercut, 50, 0, 1_000_000_000L, v -> c.undercut = v);
        y = toggleRow(g, mx, my, y, "Percent undercut", c.undercutMode == 1, () -> { c.undercutMode = c.undercutMode == 1 ? 0 : 1; c.save(); });
        y = decRow(g, mx, my, y, "Percent undercut (%)", "s.pct", c.undercutPercent, 0.5, 0, 50, v -> c.undercutPercent = v);
        y = numRow(g, mx, my, y, "Default minimum price ($)", "s.min", c.minPrice, 500, 0, 1_000_000_000L, v -> c.minPrice = v);
        y = numRow(g, mx, my, y, "Min profit per item ($)", "s.minprofit", c.minProfit, 100, 0, 1_000_000_000L, v -> c.minProfit = v);
        y = toggleRow(g, mx, my, y, "Outlier protection", c.outlierProtect, () -> { c.outlierProtect = !c.outlierProtect; c.save(); });
        y = numRow(g, mx, my, y, "Lowball threshold (%)", "s.lowball", c.outlierPercent, 5, 5, 90, v -> c.outlierPercent = (int) (long) v);

        y = section(g, y + 2, "SCANNING");
        y = toggleRow(g, mx, my, y, "Auto sort (lowest first)", c.autoSort, () -> { c.autoSort = !c.autoSort; c.save(); });
        y = toggleRow(g, mx, my, y, "Per-item pricing (stacks)", c.perItemPrice, () -> { c.perItemPrice = !c.perItemPrice; c.save(); });
        y = numRow(g, mx, my, y, "Max pages to read", "s.pages", c.scanPages, 1, 1, 20, v -> c.scanPages = (int) (long) v);
        y = numRow(g, mx, my, y, "Scan delay (ms)", "s.delay", c.scanDelayMs, 50, 100, 10000, v -> c.scanDelayMs = (int) (long) v);
        y = numRow(g, mx, my, y, "Auction timeout (s)", "s.timeout", c.timeoutMs / 1000, 1, 2, 60, v -> c.timeoutMs = (int) (v * 1000));
        y = numRow(g, mx, my, y, "Cooldown (s)", "s.cooldown", c.cooldownMs / 1000, 1, 2, 600, v -> c.cooldownMs = (int) (v * 1000));
        y = toggleRow(g, mx, my, y, "Debug messages in chat", c.debug, () -> { c.debug = !c.debug; c.save(); });

        y = section(g, y + 2, "DEALS");
        y = toggleRow(g, mx, my, y, "Deal finder", c.dealFinder, () -> { c.dealFinder = !c.dealFinder; c.save(); });
        y = numRow(g, mx, my, y, "Min discount (%)", "s.deal", c.dealPercent, 5, 5, 95, v -> c.dealPercent = (int) (long) v);

        y = section(g, y + 2, "APPEARANCE");
        rowBg(g, mx, my, cx, y, cw, 18, "Theme");
        for (int i = 0; i < THEME.length; i++) {
            int x = cx + cw - 8 - (THEME.length - i) * 16;
            boolean sel = c.theme == i;
            boolean hov = hover(mx, my, x, y + 3, 12, 12);
            if (sel) panel(g, x - 2, y + 1, 16, 16, 0xFFFFFFFF, 0xFFFFFFFF);
            rrect(g, x, y + 3, 12, 12, hov ? shade(THEME[i], 30) : THEME[i]);
            if (hov) tip = "Theme: " + THEME_NAMES[i];
            final int idx = i;
            addHit(x, y + 3, 12, 12, () -> { c.theme = idx; c.save(); });
        }
        y += 22;

        g.disableScissor();
        clipTop = Integer.MIN_VALUE;
        clipBot = Integer.MAX_VALUE;

        float contentH = y - startY;
        maxScroll = Math.max(0f, contentH - (vBot - vTop) + 2);
        scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget));
        scrollCur += (scrollTarget - scrollCur) * 0.35f;
        if (maxScroll > 0) {
            int trackH = vBot - vTop;
            int barH = Math.max(14, Math.round(trackH * trackH / (trackH + maxScroll)));
            int barY = vTop + Math.round((trackH - barH) * (scrollCur / maxScroll));
            g.fill(cx + cw + 3, vTop, cx + cw + 6, vBot, 0xFF150C2A);
            rrect(g, cx + cw + 3, barY, 3, barH, acc());
        }

        int by = top + H - 24;
        int bw = (cw - 6) / 2;
        outline(g, mx, my, cx, by, bw, 18, "Reset to Default", false, () -> {
            c.resetSettings();
            c.save();
            say("Settings reset to default");
        });
        primary(g, mx, my, cx + bw + 6, by, bw, 18, "Save Settings", () -> {
            c.sanitize();
            c.save();
            say("Settings saved");
        });
    }

    // ------------------------------------------------------------ items

    private void renderItems(DrawContext g, int mx, int my) {
        Config c = Config.INSTANCE;
        title(g, "Items", "Manage which items to sell");
        List<Config.ItemCfg> items = c.items;
        int n = items.size();
        Config.ItemCfg it = selItem();
        final long mult = hasShiftDown() ? 10 : 1;

        int y = y0 + 26;
        // selector
        gradButton(g, mx, my, cx, y, 24, 18, "<", shade(acc(), 30), shade(acc(), -50), () -> { itemSel = (itemSel - 1 + n) % n; stopEdit(); });
        gradButton(g, mx, my, cx + cw - 24, y, 24, 18, ">", shade(acc(), 30), shade(acc(), -50), () -> { itemSel = (itemSel + 1) % n; stopEdit(); });
        panel(g, cx + 28, y, cw - 56, 18, ROW, BORDER);
        icon(g, iconStack(it, it.name), cx + cw / 2 - 40, y + 1, 1f);
        txtCenter(g, textRenderer.trimToWidth(it.name, 90) + " (" + (itemSel + 1) + "/" + n + ")", cx + cw / 2 + 6, y + 5, 0xFFFFFFFF);

        y += 22;
        toggleAt(g, mx, my, cx, y, cw, 16, "Enabled (rotates in)", it.enabled, () -> { it.enabled = !it.enabled; c.save(); });
        y += 18;
        String m = "@bed".equals(it.match) ? (it.colorMask == 0 ? "Any bed (all colors)" : "Selected bed colors only") : it.match;
        panel(g, cx, y, cw, 14, BOX, BORDER);
        txt(g, textRenderer.trimToWidth("Matches: " + m, cw - 12), cx + 8, y + 3, MUTED);

        y += 16;
        panel(g, cx, y, cw, 16, ROW, BORDER);
        bitmap(g, I_SEARCH, cx + 7, y + 4, MUTED, 1);
        valueBox(g, mx, my, "i.search", cx + 20, y + 1, cw - 22, 14, it.search, "Search item name... (e.g. bed)", 2, 40,
                it.search == null ? "" : it.search, s -> it.search = s, false);

        y += 18;
        y = numRowX(g, mx, my, y, "Cost per item ($)", "i.cost", String.format("%,d", it.cost), String.valueOf(it.cost),
                () -> it.cost = it.cost + 100 * mult, () -> it.cost = Math.max(0, it.cost - 100 * mult),
                s -> { try { it.cost = s.isEmpty() ? 0 : Long.parseLong(s); } catch (Exception ignored) {} }, 16);
        y = numRowX(g, mx, my, y, "Minimum price ($)", "i.min", it.minPrice < 0 ? "default" : String.format("%,d", it.minPrice),
                it.minPrice < 0 ? "" : String.valueOf(it.minPrice),
                () -> it.minPrice = it.minPrice < 0 ? c.minPrice : it.minPrice + 500 * mult,
                () -> it.minPrice = it.minPrice <= 0 ? -1 : Math.max(0, it.minPrice - 500 * mult),
                s -> { try { it.minPrice = s.isEmpty() ? -1 : Long.parseLong(s); } catch (Exception ignored) {} }, 16);
        y = numRowX(g, mx, my, y, "Undercut ($)", "i.under", it.undercut < 0 ? "default" : String.format("%,d", it.undercut),
                it.undercut < 0 ? "" : String.valueOf(it.undercut),
                () -> it.undercut = it.undercut < 0 ? c.undercut : it.undercut + 50 * mult,
                () -> it.undercut = it.undercut <= 0 ? -1 : Math.max(0, it.undercut - 50 * mult),
                s -> { try { it.undercut = s.isEmpty() ? -1 : Long.parseLong(s); } catch (Exception ignored) {} }, 16);

        // bed color filter
        if ("@bed".equals(it.match)) {
            int slot = cw / 16;
            for (int i = 0; i < 16; i++) {
                int x = cx + i * slot + (slot - 14) / 2;
                boolean sel = ((it.colorMask >> i) & 1) != 0;
                boolean hov = hover(mx, my, x - 1, y - 1, 16, 16);
                if (sel) panel(g, x - 2, y - 2, 18, 18, shade(acc(), -80), acc());
                else if (hov) panel(g, x - 2, y - 2, 18, 18, ROW_H, BORDER);
                icon(g, new ItemStack(Registries.ITEM.get(Identifier.of("minecraft", Config.BED_COLORS[i] + "_bed"))), x, y, 0.875f);
                if (hov) tip = "Only sell " + Config.BED_COLORS[i].replace('_', ' ') + " beds (click again to remove)";
                final int bit = i;
                addHit(x - 2, y - 2, 18, 18, () -> { it.colorMask ^= (1 << bit); c.save(); });
            }
        } else {
            txt(g, "Filter: exact item " + it.match, cx, y + 3, DIM);
        }

        y += 20;
        primary(g, mx, my, cx, y, (cw - 6) / 2, 16, "+ Add Item in Hand", () -> addHeld(c));
        boolean canRemove = n > 1;
        gradButton(g, mx, my, cx + (cw - 6) / 2 + 6, y, (cw - 6) / 2, 16, "Remove Item",
                canRemove ? 0xFFFF6B85 : 0xFF4A3D66, canRemove ? 0xFFA0233F : 0xFF2E2446, () -> {
                    if (canRemove) {
                        items.remove(itemSel);
                        itemSel = 0;
                        c.save();
                    }
                });
        txt(g, "Items rotate each round. Empty = use Settings default.", cx, y + 20, DIM);
    }

    private void addHeld(Config c) {
        if (client == null || client.player == null) return;
        ItemStack held = client.player.getMainHandStack();
        if (held.isEmpty()) {
            say("Hold the item first (close menu, hold it, reopen).");
            return;
        }
        Config.ItemCfg n = new Config.ItemCfg();
        if (held.getItem() instanceof net.minecraft.item.BedItem) {
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
                say(n.name + " is already in the list.");
                return;
            }
        }
        n.cost = 0;
        c.items.add(n);
        itemSel = c.items.size() - 1;
        c.save();
        say("Added " + n.name + ". Set its cost below.");
    }

    // ------------------------------------------------------------ prices

    private void renderPrices(DrawContext g, int mx, int my) {
        Config c = Config.INSTANCE;
        title(g, "Prices", "Live market prices from /ah");
        List<Config.ItemCfg> items = c.items;
        int n = items.size();
        priceSel = Math.max(0, Math.min(priceSel, n - 1));
        String name = items.get(priceSel).name;

        int y = y0 + 26;
        gradButton(g, mx, my, cx, y, 20, 16, "<", shade(acc(), 30), shade(acc(), -50), () -> priceSel = (priceSel - 1 + n) % n);
        gradButton(g, mx, my, cx + 106, y, 20, 16, ">", shade(acc(), 30), shade(acc(), -50), () -> priceSel = (priceSel + 1) % n);
        panel(g, cx + 22, y, 82, 16, ROW, BORDER);
        txtCenter(g, textRenderer.trimToWidth(name, 76), cx + 63, y + 4, 0xFFFFFFFF);
        int rw = 34;
        for (int i = 0; i < RANGES.length; i++) {
            final int idx = i;
            int x = cx + cw - (RANGES.length - i) * (rw + 3) + 3;
            outline(g, mx, my, x, y, rw, 16, RANGES[i], priceRange == i, () -> priceRange = idx);
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
        y += 22;
        int cwid = (cw - 12) / 4;
        card(g, cx, y, cwid, 30, "NOW", money(now), 0xFFFFFFFF);
        card(g, cx + (cwid + 4), y, cwid, 30, "LOW", money(lo), GREEN);
        card(g, cx + 2 * (cwid + 4), y, cwid, 30, "HIGH", money(hi), RED);
        card(g, cx + 3 * (cwid + 4), y, cwid, 30, "AVERAGE", money(avg), CYAN);

        y += 34;
        drawChart(g, mx, my, cx, y, cw, top + H - 20 - y, pts);
        if (!pts.isEmpty()) {
            txt(g, PriceLog.shortDate(pts.get(0).t), cx, top + H - 16, DIM);
            txtRight(g, PriceLog.shortDate(pts.get(pts.size() - 1).t), cx + cw, top + H - 16, DIM);
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
        panel(g, x, y, w, h, BOX, BORDER);
        if (pts.isEmpty()) {
            txt(g, "No price data yet. Run the mod to collect prices.", x + 10, y + h / 2 - 4, DIM);
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

        int ix = x + 34, iw = w - 42, ytop = y + 8, bot = y + h - 8, ih = bot - ytop;
        for (int i = 0; i <= 4; i++) {
            int gy = ytop + ih * i / 4;
            g.fill(ix, gy, ix + iw, gy + 1, 0xFF22163F);
            long v = realMax - (realMax - realMin) * i / 4;
            txtRight(g, Profit.compact(v), ix - 3, gy - 4, DIM);
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
            g.fill(ix + col, lo2, ix + col + 1, hi2, shade(a, 70));
            prevPy = py;
            lastPy = py;
        }
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        int mxp = ix + iw - 1;
        g.fill(mxp - 3, lastPy - 2, mxp + 2, lastPy + 4, alpha(a, 40 + Math.round(pulse * 80)));
        g.fill(mxp - 1, lastPy, mxp + 1, lastPy + 2, 0xFFFFFFFF);

        if (hover(mx, my, ix, y, iw, h)) {
            int col = mx - ix;
            long t = tmin + (tmax - tmin) * col / Math.max(1, iw - 1);
            int k = 0;
            while (k < pts.size() - 2 && pts.get(k + 1).t <= t) k++;
            double v = pts.size() == 1 ? pts.get(0).p : interp(pts, k, t);
            g.fill(mx, ytop, mx + 1, bot, 0x88FFFFFF);
            txtRight(g, PriceLog.shortDate(t) + "  " + Profit.compact(Math.round(v)), x + w - 6, y + 3, 0xFFFFFFFF);
        } else {
            txtRight(g, pts.size() + " points", x + w - 6, y + 3, DIM);
        }
    }

    // ------------------------------------------------------------ deals

    private void renderDeals(DrawContext g, int mx, int my) {
        title(g, "Deals", "Listings found far below market");
        outline(g, mx, my, cx + cw - 50, y0 + 14, 50, 14, "Clear", false, () -> Deals.clear());

        int y = y0 + 32;
        panel(g, cx, y, cw, 14, 0xFF170D2D, BORDER);
        txt(g, "TIME", cx + 6, y + 3, acc());
        txt(g, "ITEM", cx + 74, y + 3, acc());
        txt(g, "PRICE", cx + 140, y + 3, acc());
        txt(g, "RESELL", cx + 196, y + 3, acc());
        txtRight(g, "PROFIT", cx + cw - 6, y + 3, acc());

        List<Deals.Entry> list = Deals.entries();
        if (list.isEmpty()) {
            txt(g, "No deals yet. Listings far below market show up here.", cx + 6, y + 24, MUTED);
            txt(g, "Turn Auto Sell OFF to just hunt for deals.", cx + 6, y + 36, DIM);
        }
        for (int i = 0; i < 11; i++) {
            int idx = list.size() - 1 - i;
            if (idx < 0) break;
            Deals.Entry e = list.get(idx);
            int ry = y + 17 + i * 14;
            if (i % 2 == 0) g.fill(cx, ry - 1, cx + cw, ry + 12, 0xFF170D2D);
            txt(g, Deals.time(e.time), cx + 6, ry + 2, 0xFFDDD0F5);
            txt(g, textRenderer.trimToWidth(e.item, 60), cx + 74, ry + 2, 0xFFC9B6F5);
            txt(g, Profit.compact(e.price), cx + 140, ry + 2, 0xFFFFFFFF);
            txt(g, Profit.compact(e.resell), cx + 196, ry + 2, 0xFFC9B6F5);
            txtRight(g, Profit.compactSigned(e.potential), cx + cw - 6, ry + 2, GREEN);
        }
    }

    // ------------------------------------------------------------ alerts

    private void renderAlerts(DrawContext g, int mx, int my) {
        Config c = Config.INSTANCE;
        title(g, "Alerts", "Get notified when items are listed");
        int y = y0 + 28;
        y = toggleRow(g, mx, my, y, "Chat notifications", c.notifications, () -> { c.notifications = !c.notifications; c.save(); });
        y = toggleRow(g, mx, my, y, "Sound alert", c.soundAlert, () -> { c.soundAlert = !c.soundAlert; c.save(); });
        y = toggleRow(g, mx, my, y, "Popup toast", c.toastAlert, () -> { c.toastAlert = !c.toastAlert; c.save(); });

        y += 6;
        txt(g, "Discord webhook URL (optional)", cx, y, TXT);
        y += 12;
        panel(g, cx, y, cw, 18, ROW, BORDER);
        valueBox(g, mx, my, "a.hook", cx + 2, y + 2, cw - 22, 14, c.discordWebhook, "https://discord.com/api/webhooks/...", 2, 220,
                c.discordWebhook == null ? "" : c.discordWebhook, s -> c.discordWebhook = s.trim(), false);
        bitmap(g, I_LINK, cx + cw - 15, y + 5, MUTED, 1);

        y += 26;
        primary(g, mx, my, cx, y, cw, 20, "Send Test Alert", () -> {
            c.save();
            AutoSeller.testAlert(client);
        });
        txt(g, "Alerts fire when an item is listed for sale or a deal is found.", cx, y + 28, DIM);
        txt(g, "The webhook sends the same message to your Discord channel.", cx, y + 40, DIM);
    }

    // ------------------------------------------------------------ profit

    private void renderProfit(DrawContext g, int mx, int my) {
        title(g, "Profit", "Track your earnings");
        long now = System.currentTimeMillis();
        long start = profitPeriod == 0 ? Profit.startOfToday() : profitPeriod == 1 ? now - 7L * 24 * 3600 * 1000 : 0;

        outline(g, mx, my, cx + cw - 72, y0 + 12, 72, 16, PERIODS[profitPeriod] + "  v", false, () -> profitPeriod = (profitPeriod + 1) % 3);

        int y = y0 + 32;
        long total = animNum("pTotal" + profitPeriod, Profit.since(start));
        panel(g, cx, y, cw, 42, ROW, BORDER);
        g.fillGradient(cx + 1, y + 1, cx + cw - 1, y + 20, alpha(GREEN, 18), alpha(GREEN, 0));
        for (int k = 0; k < 3; k++) {
            int cy = y + 24 - k * 6;
            rrect(g, cx + 10, cy, 20, 9, 0xFFB8860B);
            rrect(g, cx + 11, cy + 1, 18, 5, GOLD);
            g.fill(cx + 13, cy + 2, cx + 22, cy + 3, 0xFFFFE9A0);
        }
        txt(g, "TOTAL PROFIT", cx + 40, y + 6, MUTED);
        big(g, Profit.signed(total), cx + 40, y + 19, 1.8f, profitColor(total));

        y += 46;
        int cwid = (cw - 8) / 3;
        long today = Profit.since(Profit.startOfToday());
        long week = Profit.since(now - 7L * 24 * 3600 * 1000);
        card(g, cx, y, cwid, 30, "TODAY", Profit.compactSigned(today), profitColor(today));
        card(g, cx + cwid + 4, y, cwid, 30, "7 DAYS", Profit.compactSigned(week), profitColor(week));
        card(g, cx + 2 * (cwid + 4), y, cwid, 30, "AVG / SALE", Profit.compactSigned(Profit.average()), profitColor(Profit.average()));

        y += 34;
        txt(g, "Items sold: " + String.format("%,d", Profit.countSince(start)), cx + 2, y, TXT);
        bitmap(g, I_TROPHY, cx + cw - 118, y - 1, GOLD, 1);
        txtRight(g, "Best sale: " + Profit.compactSigned(Profit.bestSince(start)), cx + cw - 2, y, GREEN);

        y += 14;
        int chartH = top + H - 38 - y;
        panel(g, cx, y, cw, chartH, BOX, BORDER);
        long[] daily = Profit.daily(7);
        long lo = 0, hi = 0;
        for (long v : daily) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        if (hi == lo) hi = lo + 1;
        float grow = ease(Math.min(1f, (now - Math.max(openAt, tabSwitchAt)) / 600f));
        float scale = (chartH - 10) / (float) (hi - lo);
        int baseY = y + 5 + Math.round(hi * scale);
        int slot = (cw - 8) / 7;
        for (int i = 0; i < 7; i++) {
            int bx = cx + 4 + i * slot + 6;
            int bw = slot - 12;
            int hgt = Math.round(Math.abs(daily[i]) * scale * grow);
            if (daily[i] >= 0) g.fillGradient(bx, baseY - hgt, bx + bw, baseY, 0xFF5CF0A0, 0xFF1C9A58);
            else g.fillGradient(bx, baseY, bx + bw, baseY + hgt, 0xFFFF6B85, 0xFFA0233F);
            String lbl = Profit.dayLabel(6 - i);
            txt(g, lbl, bx + (bw - textRenderer.getWidth(lbl)) / 2, y + chartH + 3, i == 6 ? 0xFFFFFFFF : DIM);
        }
        g.fill(cx + 2, baseY, cx + cw - 2, baseY + 1, BORDER);

        outline(g, mx, my, cx + cw - 96, top + H - 22, 96, 14, resetArmed ? "Click to CONFIRM" : "Reset all data", resetArmed, () -> {
            if (!resetArmed) resetArmed = true;
            else { Profit.clear(); resetArmed = false; }
        });
    }

    // ------------------------------------------------------------ history

    private void renderHistory(DrawContext g, int mx, int my) {
        title(g, "History", "Recent sales and profit");

        int y = y0 + 28;
        // search
        panel(g, cx, y, 136, 16, ROW, BORDER);
        bitmap(g, I_SEARCH, cx + 6, y + 4, MUTED, 1);
        valueBox(g, mx, my, "h.search", cx + 18, y + 1, 116, 14, histSearch, "Search history...", 2, 30, histSearch, s -> { histSearch = s; histPage = 0; }, false);
        // items per page
        txt(g, "Items per page", cx + 142, y + 4, MUTED);
        outline(g, mx, my, cx + 212, y, 34, 16, PER_PAGE[perPageSel] + " v", false, () -> { perPageSel = (perPageSel + 1) % PER_PAGE.length; histPage = 0; });

        // filter
        List<Profit.Entry> all = Profit.entries();
        List<Profit.Entry> list = new ArrayList<>();
        String q = histSearch.toLowerCase();
        for (int i = all.size() - 1; i >= 0; i--) {
            Profit.Entry e = all.get(i);
            if (q.isEmpty() || (Profit.date(e.time) + " " + e.item + " " + e.price + " " + e.profit).toLowerCase().contains(q)) list.add(e);
        }
        int per = PER_PAGE[perPageSel];
        int pages = Math.max(1, (list.size() + per - 1) / per);
        histPage = Math.max(0, Math.min(histPage, pages - 1));

        // table
        y += 20;
        panel(g, cx, y, cw, 14, 0xFF170D2D, BORDER);
        txt(g, "DATE", cx + 6, y + 3, MUTED);
        txt(g, "ITEM", cx + 110, y + 3, MUTED);
        txt(g, "LISTED AT", cx + 164, y + 3, MUTED);
        txt(g, "PRICE", cx + 212, y + 3, MUTED);
        txt(g, "PROFIT", cx + 250, y + 3, MUTED);
        txtRight(g, "ACTIONS", cx + cw - 5, y + 3, MUTED);

        if (list.isEmpty()) txt(g, "No sales yet. They will show up here.", cx + 6, y + 22, MUTED);
        for (int i = 0; i < per; i++) {
            int idx = histPage * per + i;
            if (idx >= list.size()) break;
            Profit.Entry e = list.get(idx);
            int ry = y + 16 + i * 13;
            g.fill(cx, ry, cx + cw, ry + 13, i % 2 == 0 ? 0xFF160C2A : 0xFF1B1033);
            txt(g, Profit.date(e.time), cx + 6, ry + 3, 0xFFDDD0F5);
            icon(g, iconStack(cfgByName(e.item), e.item), cx + 108, ry + 2, 0.55f);
            txt(g, textRenderer.trimToWidth(e.item == null ? "Bed" : e.item, 38), cx + 119, ry + 3, 0xFFC9B6F5);
            int count = Math.max(1, e.count);
            txt(g, Profit.compact(e.price), cx + 164, ry + 3, 0xFFFFFFFF);
            txt(g, Profit.compact(e.price / count), cx + 212, ry + 3, 0xFFFFFFFF);
            txt(g, Profit.compactSigned(e.profit), cx + 250, ry + 3, profitColor(e.profit));
            boolean hov = hover(mx, my, cx + cw - 18, ry, 14, 13);
            bitmap(g, I_COPY, cx + cw - 15, ry + 3, hov ? 0xFFFFFFFF : MUTED, 1);
            if (hov) tip = "Copy this sale";
            final String copy = Profit.date(e.time) + " | " + e.item + " | " + Profit.money(e.price) + " | " + Profit.signed(e.profit);
            addHit(cx + cw - 18, ry, 14, 13, () -> {
                if (client != null) client.keyboard.setClipboard(copy);
                say("Copied");
            });
        }

        int by = top + H - 24;
        outline(g, mx, my, cx + cw - 150, by, 46, 16, "Newer", false, () -> { if (histPage > 0) histPage--; });
        txtCenter(g, "Page " + (histPage + 1) + " / " + pages, cx + cw - 76, by + 4, MUTED);
        primary(g, mx, my, cx + cw - 46, by, 46, 16, "Older", () -> { if (histPage < pages - 1) histPage++; });
        txt(g, list.size() + " sale(s)", cx, by + 4, DIM);
    }
}
