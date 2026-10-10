package com.marketscout;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Draws deal highlights and per-item prices on top of any chest-style GUI (like the auction house). Clicks nothing. */
public final class Overlay {
    private Overlay() {}

    private record Row(int slotX, int slotY, long unit, boolean deal, boolean cheapest, long potential, int disc) {}

    private static List<Row> cache = new ArrayList<>();
    private static Market.Stats cacheStats = null;
    private static long cacheAt = 0;
    private static Object cacheKey = null;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof GenericContainerScreen gs) {
                ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> render(client, gs, ctx, mx, my));
            }
        });
    }

    private static void compute(GenericContainerScreenHandler h) {
        List<Row> rows = new ArrayList<>();
        List<long[]> raw = new ArrayList<>(); // {slotX, slotY, unit}
        boolean perItem = Config.INSTANCE.perItem;
        for (Slot s : h.slots) {
            if (s.inventory instanceof PlayerInventory) continue;
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            PriceParser.Result r = PriceParser.analyzeLore(st);
            if (r == null) continue;
            int cnt = Math.max(1, st.getCount());
            long unit = (perItem && !r.perUnit()) ? r.price() / cnt : r.price();
            if (unit > 0) raw.add(new long[] { s.x, s.y, unit });
        }
        if (raw.isEmpty()) {
            cache = rows;
            cacheStats = null;
            return;
        }
        List<Long> units = new ArrayList<>();
        for (long[] r : raw) units.add(r[2]);
        units.sort(Comparator.naturalOrder());
        Market.Stats st = Market.of(units);
        for (long[] r : raw) {
            long u = r[2];
            boolean deal = st.isDeal(u);
            rows.add(new Row((int) r[0], (int) r[1], u, deal, u == st.cheapest(),
                    deal ? st.potential(u) : 0, st.discount(u)));
        }
        cache = rows;
        cacheStats = st;
    }

    private static void box(DrawContext g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void render(MinecraftClient mc, GenericContainerScreen gs, DrawContext g, int mx, int my) {
        if (!Config.INSTANCE.overlay) return;
        GenericContainerScreenHandler h = gs.getScreenHandler();
        long now = System.currentTimeMillis();
        if (cacheKey != h || now - cacheAt > 250) {
            compute(h);
            cacheKey = h;
            cacheAt = now;
        }
        if (cacheStats == null || cache.isEmpty()) return;

        int rows = h.getRows();
        int bgH = 114 + rows * 18;
        int gx = (gs.width - 176) / 2;
        int gy = (gs.height - bgH) / 2;
        TextRenderer tr = mc.textRenderer;
        Market.Stats st = cacheStats;

        var m = g.getMatrices();
        m.push();
        m.translate(0, 0, 300);

        Row hover = null;
        for (Row r : cache) {
            int sx = gx + r.slotX(), sy = gy + r.slotY();
            if (r.deal()) {
                g.fill(sx, sy, sx + 16, sy + 16, 0x3A4DE08A);
                box(g, sx - 1, sy - 1, 18, 18, 0xFF4DE08A);
            }
            if (r.cheapest()) box(g, sx - 1, sy - 1, 18, 18, 0xFFFFD84D);
            if (Config.INSTANCE.labels) {
                String label = Fmt.tiny(r.unit());
                m.push();
                m.translate(sx + 16 - tr.getWidth(label) * 0.55f, sy + 10.5f, 0);
                m.scale(0.55f, 0.55f, 1f);
                g.drawText(tr, label, 0, 0, r.deal() ? 0xFF7DFFB0 : 0xFFFFFFFF, true);
                m.pop();
            }
            if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) hover = r;
        }

        // summary bar
        String summary = "Scout  |  cheapest " + Fmt.compact(st.cheapest()) + "  median " + Fmt.compact(st.median())
                + (st.dealsPossible() ? "  deals " + countDeals() : "  (need 3+ listings for deals)");
        int bw = tr.getWidth(summary) + 12;
        int by = gy >= 22 ? gy - 20 : gy + bgH + 4;
        g.fill(gx, by, gx + bw, by + 16, 0xE00E1020);
        box(g, gx, by, bw, 16, 0xFF4C8DFF);
        g.drawText(tr, summary, gx + 6, by + 4, 0xFFE6E8FF, true);

        // hover tooltip for deals
        if (hover != null && hover.deal()) {
            String t1 = "DEAL  " + hover.disc() + "% under market";
            String t2 = "Resell ~" + Fmt.money(st.net()) + "  =  +" + Fmt.money(hover.potential());
            int tw = Math.max(tr.getWidth(t1), tr.getWidth(t2)) + 12;
            int tx = Math.min(mx + 12, gs.width - tw - 4);
            int ty = Math.max(4, my - 30);
            m.translate(0, 0, 100);
            g.fill(tx, ty, tx + tw, ty + 26, 0xF00E1020);
            box(g, tx, ty, tw, 26, 0xFF4DE08A);
            g.drawText(tr, t1, tx + 6, ty + 4, 0xFF7DFFB0, true);
            g.drawText(tr, t2, tx + 6, ty + 14, 0xFFFFFFFF, true);
        }
        m.pop();
    }

    private static int countDeals() {
        int n = 0;
        for (Row r : cache) if (r.deal()) n++;
        return n;
    }
}
