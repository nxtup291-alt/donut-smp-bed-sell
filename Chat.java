package com.marketscout;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Watches chat for buy/sell messages so flips are tracked automatically. */
public final class Chat {
    private Chat() {}

    public static final Deque<String> MONEY = new ArrayDeque<>(); // recent chat lines containing "$"
    public static String buyError = "";
    public static String sellError = "";
    private static Pattern buy, sell;
    private static String compiledBuy = null, compiledSell = null;

    public static void compile() {
        Config c = Config.INSTANCE;
        if (!c.buyRegex.equals(compiledBuy)) {
            compiledBuy = c.buyRegex;
            buy = null;
            buyError = "";
            if (!c.buyRegex.isBlank()) {
                try { buy = Pattern.compile(c.buyRegex); } catch (Exception e) { buyError = "Invalid pattern"; }
            }
        }
        if (!c.sellRegex.equals(compiledSell)) {
            compiledSell = c.sellRegex;
            sell = null;
            sellError = "";
            if (!c.sellRegex.isBlank()) {
                try { sell = Pattern.compile(c.sellRegex); } catch (Exception e) { sellError = "Invalid pattern"; }
            }
        }
    }

    private static String group(Matcher m, String name) {
        try { return m.group(name); } catch (Exception e) { return null; }
    }

    public static void onMessage(MinecraftClient mc, String raw) {
        String s = PriceParser.clean(raw);
        if (s.isEmpty()) return;
        if (s.indexOf('$') >= 0) {
            if (MONEY.isEmpty() || !MONEY.peekFirst().equals(s)) MONEY.addFirst(s);
            while (MONEY.size() > 12) MONEY.removeLast();
        }
        Config c = Config.INSTANCE;
        if (!c.trackChat) return;
        compile();
        try {
            if (buy != null) {
                Matcher m = buy.matcher(s);
                if (m.find()) {
                    String p = group(m, "price");
                    var price = p == null ? java.util.OptionalLong.empty() : PriceParser.parseText("$" + p);
                    if (price.isPresent()) {
                        Flips.onBuy(group(m, "item"), price.getAsLong());
                        Alerts.chat(mc, "Tracked buy: " + Fmt.money(price.getAsLong()));
                        return;
                    }
                }
            }
            if (sell != null) {
                Matcher m = sell.matcher(s);
                if (m.find()) {
                    String p = group(m, "price");
                    var price = p == null ? java.util.OptionalLong.empty() : PriceParser.parseText("$" + p);
                    if (price.isPresent()) {
                        Flips.Entry e = Flips.onSell(group(m, "item"), price.getAsLong());
                        if (e != null) {
                            Alerts.chat(mc, "Flip done: " + e.item + " " + Fmt.signed(e.profit) + " | total " + Fmt.signed(Flips.total()));
                            Alerts.fire(mc, "Flip done: " + e.item, Fmt.signed(e.profit) + "  |  Total " + Fmt.signed(Flips.total()),
                                    "Flip done: " + e.item + " " + Fmt.signed(e.profit));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}
