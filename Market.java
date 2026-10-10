package com.marketscout;

import java.util.List;

/** Price statistics for one page/scan of listings (all prices are per item). */
public final class Market {
    private Market() {}

    public record Stats(int n, long cheapest, long second, long median, long limit, long resell, boolean dealsPossible) {
        public boolean isDeal(long unit) { return dealsPossible && unit <= limit; }

        /** What you'd realistically get when reselling (after undercut and sale tax). */
        public long net() {
            Config c = Config.INSTANCE;
            long v = resell - c.resellUndercut;
            return Math.round(v * (100.0 - c.saleTaxPercent) / 100.0);
        }

        public long potential(long unit) { return net() - unit; }

        public int discount(long unit) {
            return median <= 0 ? 0 : (int) Math.max(0, (median - unit) * 100 / median);
        }
    }

    /** sorted = unit prices, lowest first (must not be empty). */
    public static Stats of(List<Long> sorted) {
        Config c = Config.INSTANCE;
        int n = sorted.size();
        long cheapest = sorted.get(0);
        long second = n > 1 ? sorted.get(1) : cheapest;
        long median = sorted.get(n / 2);
        boolean possible = n >= 3;
        long limit = median * (100 - c.dealPercent) / 100;
        long resell = median;
        if (possible) {
            for (long u : sorted) {
                if (u > limit) { resell = u; break; }
            }
        }
        return new Stats(n, cheapest, second, median, limit, resell, possible);
    }
}
