package com.marketscout;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads prices such as $25,000 / $25.0K / $1.5M from item lore (with or without formatting codes). */
public final class PriceParser {
    private PriceParser() {}

    /** price = amount found, perUnit = the line said "each", line = the text it came from. */
    public record Result(long price, boolean perUnit, String line, List<String> lines) {}

    private static final Pattern CODES = Pattern.compile("\u00a7.");
    private static final Pattern DOLLAR = Pattern.compile(
            "\\$\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*([kKmMbBtT])?(?![A-Za-z])");
    private static final Pattern PLAIN = Pattern.compile(
            "(?i)price\\D{0,10}(\\d[\\d,]*(?:\\.\\d+)?)\\s*([kmbt])?(?![a-z])");

    // Lines that say "this is the price".
    private static final String[] PREFERRED = { "price", "buy now", "buyout", "cost", "listed for" };
    // Lines that are never the price (fees, balances, seller info...).
    private static final String[] STRICT_EXCLUDE = { "seller", "sold by", "listed by", "balance", "fee", "tax", "bid", "purse", "your money" };
    // Extra lines to skip when we have to guess.
    private static final String[] LOOSE_EXCLUDE = { "ends", "expire", "time left", "remaining", "click", "shift" };

    public static String clean(String s) {
        return CODES.matcher(s.replace('\u00a0', ' ')).replaceAll("").trim();
    }

    private static boolean containsAny(String low, String[] words) {
        for (String w : words) if (low.contains(w)) return true;
        return false;
    }

    private static List<String> loreLines(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text t : lore.lines()) lines.add(clean(t.getString()));
        }
        return lines;
    }

    /** Full analysis of a listing. Returns null when no price could be read. */
    public static Result analyze(ItemStack stack, PlayerEntity player) {
        List<String> lore = loreLines(stack);
        Result r = parseLines(lore);
        if (r != null) return r;
        try {
            List<String> tip = new ArrayList<>();
            for (Text t : stack.getTooltip(Item.TooltipContext.DEFAULT, player, TooltipType.BASIC)) {
                tip.add(clean(t.getString()));
            }
            return parseLines(tip);
        } catch (Exception e) {
            return null;
        }
    }

    /** Lore-only analysis (fast, safe to call every frame). */
    public static Result analyzeLore(ItemStack stack) {
        return parseLines(loreLines(stack));
    }

    public static OptionalLong fromStack(ItemStack stack, PlayerEntity player) {
        Result r = analyze(stack, player);
        return r == null ? OptionalLong.empty() : OptionalLong.of(r.price());
    }

    /** True if any lore/tooltip line mentions the given player name (e.g. "Seller: Name"). */
    public static boolean mentionsPlayer(ItemStack stack, PlayerEntity player, String name) {
        if (name == null || name.isEmpty()) return false;
        Pattern pat = Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(name.toLowerCase(Locale.ROOT)) + "(?![a-z0-9_])");
        List<String> lines = loreLines(stack);
        try {
            for (Text t : stack.getTooltip(Item.TooltipContext.DEFAULT, player, TooltipType.BASIC)) lines.add(clean(t.getString()));
        } catch (Exception ignored) {}
        for (String l : lines) {
            if (pat.matcher(l.toLowerCase(Locale.ROOT)).find()) return true;
        }
        return false;
    }

    public static OptionalLong parse(List<String> lines) {
        Result r = parseLines(lines);
        return r == null ? OptionalLong.empty() : OptionalLong.of(r.price());
    }

    public static OptionalLong parseText(String s) {
        return parse(List.of(clean(s)));
    }

    private static Result parseLines(List<String> lines) {
        // Pass 1: a line that says "price"/"buy now"/... and has a $ amount
        for (String l : lines) {
            String low = l.toLowerCase(Locale.ROOT);
            if (containsAny(low, PREFERRED) && !containsAny(low, STRICT_EXCLUDE)) {
                Result r = tryLine(DOLLAR, l, lines);
                if (r != null) return r;
            }
        }
        // Pass 2: any other line with a $ amount (but not seller/fee/time lines)
        for (String l : lines) {
            String low = l.toLowerCase(Locale.ROOT);
            if (!containsAny(low, STRICT_EXCLUDE) && !containsAny(low, LOOSE_EXCLUDE)) {
                Result r = tryLine(DOLLAR, l, lines);
                if (r != null) return r;
            }
        }
        // Pass 3: "Price: 25,000" without a currency symbol
        for (String l : lines) {
            String low = l.toLowerCase(Locale.ROOT);
            if (low.contains("price") && !containsAny(low, STRICT_EXCLUDE)) {
                Result r = tryLine(PLAIN, l, lines);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static Result tryLine(Pattern p, String line, List<String> all) {
        Matcher m = p.matcher(line);
        if (!m.find()) return null;
        long value = toLong(m.group(1), m.group(2));
        if (value <= 0) return null;
        int found = 1;
        while (m.find()) found++;
        String low = line.toLowerCase(Locale.ROOT);
        // "$250 each" (only one amount) means per item; "$25,000 ($250 each)" means the first is the total.
        boolean perUnit = found == 1 && (low.contains("each") || low.contains("per item")
                || low.contains("/ea") || low.contains("per unit"));
        return new Result(value, perUnit, line, all);
    }

    private static long toLong(String num, String suffix) {
        try {
            BigDecimal v = new BigDecimal(num.replace(",", ""));
            if (suffix != null && !suffix.isEmpty()) {
                long mult = switch (Character.toLowerCase(suffix.charAt(0))) {
                    case 'k' -> 1_000L;
                    case 'm' -> 1_000_000L;
                    case 'b' -> 1_000_000_000L;
                    case 't' -> 1_000_000_000_000L;
                    default -> 1L;
                };
                v = v.multiply(BigDecimal.valueOf(mult));
            }
            return v.setScale(0, RoundingMode.DOWN).longValueExact();
        } catch (Exception e) {
            return -1;
        }
    }
}
