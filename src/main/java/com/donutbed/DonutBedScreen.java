package com.donutbed;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class DonutBedScreen extends Screen {
    private static final int W = 280;
    private static final int H = 262;

    private final List<TextFieldWidget> fields = new ArrayList<>();
    private int left, top;

    public DonutBedScreen() {
        super(Text.literal("Donut Bed Client"));
    }

    private static Text onOff(String label, boolean v) {
        return Text.literal(label + ": ").append(Text.literal(v ? "ON" : "OFF")
                .formatted(v ? Formatting.GREEN : Formatting.RED));
    }

    @Override
    protected void init() {
        Config c = Config.INSTANCE;
        fields.clear();
        left = (width - W) / 2;
        top = Math.max(10, (height - H) / 2);
        int bw = (W - 6) / 2;

        toggle(left, top + 24, bw, "Master", c.enabled, v -> c.enabled = v);
        toggle(left + bw + 6, top + 24, bw, "Auto Sell", c.autoSell, v -> c.autoSell = v);
        toggle(left, top + 48, bw, "Repeat", c.repeat, v -> c.repeat = v);
        toggle(left + bw + 6, top + 48, bw, "Notify", c.notifications, v -> c.notifications = v);

        int y = top + 78;
        field(y, String.valueOf(c.undercut), s -> c.undercut = num(s, c.undercut));
        field(y + 22, String.valueOf(c.minPrice), s -> c.minPrice = num(s, c.minPrice));
        field(y + 44, String.valueOf(c.scanDelayMs), s -> c.scanDelayMs = (int) Math.min(num(s, c.scanDelayMs), 1_000_000));
        field(y + 66, String.valueOf(c.timeoutMs / 1000), s -> c.timeoutMs = (int) Math.min(num(s, c.timeoutMs / 1000) * 1000, 1_000_000));
        field(y + 88, String.valueOf(c.cooldownMs / 1000), s -> c.cooldownMs = (int) Math.min(num(s, c.cooldownMs / 1000) * 1000, 1_000_000));
    }

    private static long num(String s, long old) {
        if (s == null || s.isEmpty()) return old;
        try { return Long.parseLong(s); } catch (Exception e) { return old; }
    }

    private void toggle(int x, int y, int w, String label, boolean initial, Consumer<Boolean> setter) {
        boolean[] state = { initial };
        ButtonWidget b = ButtonWidget.builder(onOff(label, initial), btn -> {
            state[0] = !state[0];
            setter.accept(state[0]);
            btn.setMessage(onOff(label, state[0]));
            Config.INSTANCE.save();
        }).dimensions(x, y, w, 20).build();
        addDrawableChild(b);
    }

    private void field(int y, String value, Consumer<String> onChange) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, left + W - 110, y, 110, 18, Text.empty());
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
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        super.renderBackground(ctx, mx, my, delta);
        ctx.fill(left - 10, top - 8, left + W + 10, top + H + 8, 0xE6101018);
        ctx.fill(left - 10, top - 8, left + W + 10, top - 5, 0xFF3AA0FF);
    }

    private static String money(long v) {
        return v < 0 ? "-" : String.format("$%,d", v);
    }

    private int statusColor(AutoSeller.Status s) {
        return switch (s) {
            case IDLE -> 0xFFAAAAAA;
            case SCANNING -> 0xFFFFFF55;
            case PRICE_FOUND -> 0xFF55FF55;
            case SELLING -> 0xFF55FFFF;
            case ERROR -> 0xFFFF5555;
        };
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Donut Bed Client"), width / 2, top + 6, 0xFFFFFFFF);

        String[] labels = { "Undercut ($)", "Minimum price ($)", "Scan delay (ms)", "Auction timeout (s)", "Cooldown (s)" };
        for (int i = 0; i < labels.length; i++) {
            ctx.drawTextWithShadow(textRenderer, Text.literal(labels[i]), left, top + 78 + i * 22 + 5, 0xFFDDDDDD);
        }

        int y = top + 78 + 5 * 22 + 8;
        AutoSeller.Status st = AutoSeller.status;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Status: " + st.name().replace('_', ' ')), left, y, statusColor(st));
        ctx.drawTextWithShadow(textRenderer, Text.literal("Cheapest bed: " + money(AutoSeller.cheapest)), left, y + 13, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Selling price: " + money(AutoSeller.sellPrice)), left, y + 26, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(AutoSeller.message, W)), left, y + 39,
                st == AutoSeller.Status.ERROR ? 0xFFFF7777 : 0xFFAAAAAA);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Close this menu to start. Hold a bed to sell."), left, y + 52, 0xFF777777);
    }
}
