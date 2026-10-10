package com.marketscout;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Fmt {
    private Fmt() {}

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd  h:mm a", Locale.US).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter SHORT =
            DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.US).withZone(ZoneId.systemDefault());

    public static String money(long v) { return String.format(Locale.US, "$%,d", v); }

    public static String compact(long v) {
        long a = Math.abs(v);
        String s = a >= 1_000_000 ? String.format(Locale.US, "%.2fM", a / 1_000_000.0) : String.format(Locale.US, "%,d", a);
        return (v < 0 ? "-" : "") + "$" + s;
    }

    /** Very short: 24K, 1.5M (used on slot labels). */
    public static String tiny(long v) {
        if (v >= 1_000_000) return String.format(Locale.US, "%.1fM", v / 1_000_000.0);
        if (v >= 10_000) return (v / 1000) + "K";
        if (v >= 1_000) return String.format(Locale.US, "%.1fK", v / 1000.0);
        return String.valueOf(v);
    }

    public static String signed(long v) { return (v >= 0 ? "+" : "-") + money(Math.abs(v)); }
    public static String compactSigned(long v) { return (v >= 0 ? "+" : "-") + compact(Math.abs(v)); }
    public static String date(long t) { return DATE.format(Instant.ofEpochMilli(t)); }
    public static String shortDate(long t) { return SHORT.format(Instant.ofEpochMilli(t)); }

    public static String dayLabel(int daysAgo) {
        return LocalDate.now().minusDays(daysAgo).format(DateTimeFormatter.ofPattern("EEE", Locale.US));
    }

    public static String age(long t) {
        long s = Math.max(0, (System.currentTimeMillis() - t) / 1000);
        if (s < 60) return s + "s";
        if (s < 3600) return (s / 60) + "m";
        if (s < 86400) return (s / 3600) + "h";
        return (s / 86400) + "d";
    }
}
