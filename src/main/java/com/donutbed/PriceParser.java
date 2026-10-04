package com.donutbed;

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
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Locale;

/** Reads prices such as $25,000 / $25.0K / $1.5M from item lore (with or without formatting codes). */
public final class PriceParser {
    private PriceParser() {}

    private static final Pattern CODES = Pattern.compile("\u00a7.");
    private static final Pattern DOLLAR = Pattern.compile(
            "\\$\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*([kKmMbBtT])?(?![A-Za-z])");
    private static final Pattern PLAIN = Pattern.compile(
            "(?i)price\\D{0,10}(\\d[\\d,]*(?:\\.\\d+)?)\\s*([kmbt])?(?![a-z])");

    public static String clean(String s) {
        return CODES.matcher(s.replace('\u00a0', ' ')).replaceAll("").trim();
    }

    public static OptionalLong fromStack(ItemStack stack, PlayerEntity player) {
        List<String> lines = new ArrayList<>();
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text t : lore.lines()) lines.add(clean(t.getString()));
        }
        OptionalLong r = parse(lines);
        if (r.isPresent()) return r;
        // Fallback: full tooltip text
        try {
            List<String> tip = new ArrayList<>();
            for (Text t : stack.getTooltip(Item.TooltipContext.DEFAULT, player, TooltipType.BASIC)) {
                tip.add(clean(t.getString()));
            }
            return parse(tip);
        } catch (Exception e) {
            return OptionalLong.empty();
        }
    }

    /** True if any lore/tooltip line mentions the given player name (e.g. "Seller: Name"). */
    public static boolean mentionsPlayer(ItemStack stack, PlayerEntity player, String name) {
        if (name == null || name.isEmpty()) return false;
        Pattern pat = Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(name.toLowerCase(Locale.ROOT)) + "(?![a-z0-9_])");
        List<String> lines = new ArrayList<>();
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) for (Text t : lore.lines()) lines.add(clean(t.getString()));
        try {
            for (Text t : stack.getTooltip(Item.TooltipContext.DEFAULT, player, TooltipType.BASIC)) lines.add(clean(t.getString()));
        } catch (Exception ignored) {}
        for (String l : lines) {
            if (pat.matcher(l.toLowerCase(Locale.ROOT)).find()) return true;
        }
        return false;
    }

    public static OptionalLong parse(List<String> lines) {
        // Pass 1: lines mentioning "price" with a $ amount
        for (String l : lines) {
            if (l.toLowerCase().contains("price")) {
                OptionalLong r = tryLine(DOLLAR, l);
                if (r.isPresent()) return r;
            }
        }
        // Pass 2: any line with a $ amount
        for (String l : lines) {
            OptionalLong r = tryLine(DOLLAR, l);
            if (r.isPresent()) return r;
        }
        // Pass 3: "Price: 25,000" without a currency symbol
        for (String l : lines) {
            OptionalLong r = tryLine(PLAIN, l);
            if (r.isPresent()) return r;
        }
        return OptionalLong.empty();
    }

    public static OptionalLong parseText(String s) {
        return parse(List.of(clean(s)));
    }

    private static OptionalLong tryLine(Pattern p, String line) {
        Matcher m = p.matcher(line);
        if (!m.find()) return OptionalLong.empty();
        try {
            BigDecimal v = new BigDecimal(m.group(1).replace(",", ""));
            String suf = m.group(2);
            if (suf != null) {
                long mult = switch (Character.toLowerCase(suf.charAt(0))) {
                    case 'k' -> 1_000L;
                    case 'm' -> 1_000_000L;
                    case 'b' -> 1_000_000_000L;
                    case 't' -> 1_000_000_000_000L;
                    default -> 1L;
                };
                v = v.multiply(BigDecimal.valueOf(mult));
            }
            long out = v.setScale(0, RoundingMode.DOWN).longValueExact();
            return out > 0 ? OptionalLong.of(out) : OptionalLong.empty();
        } catch (Exception e) {
            return OptionalLong.empty();
        }
    }
}
