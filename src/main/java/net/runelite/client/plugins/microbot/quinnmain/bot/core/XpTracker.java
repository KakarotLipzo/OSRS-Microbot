package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.Sk;

import java.util.EnumMap;
import java.util.Map;

/**
 * XP-per-hour tracking for the HUD's XP panel, as a total across all skills and per skill. Ported from
 * OSRS-Main; DreamBot {@code Skills} reads now go through the {@link GameApi} facade ({@link #allXp()}).
 */
public class XpTracker {

    private static final long BUCKET_MS = 30_000L;
    public static final int SERIES_LEN = 40;

    private static GameApi g() { return Game.api(); }
    private static long[] allXp() {
        try { GameApi a = g(); return a == null ? null : a.allSkillXp(); } catch (Throwable t) { return null; }
    }

    private static final class Track {
        long start = -1, last = -1, peak = 0, bucketAcc = 0;
        final long[] series = new long[SERIES_LEN];
        int filled = 0;

        void begin(long value) { start = value; last = value; }
        void accrue(long value) {
            if (last < 0) { begin(value); return; }
            bucketAcc += Math.max(0, value - last);
            last = value;
        }
        void closeBucket(long elapsedMs) {
            if (elapsedMs <= 0) return;
            long rate = Math.round(bucketAcc * 3_600_000.0 / elapsedMs);
            if (filled < SERIES_LEN) series[filled++] = bucketAcc;
            else { System.arraycopy(series, 1, series, 0, SERIES_LEN - 1); series[SERIES_LEN - 1] = bucketAcc; }
            if (rate > peak) peak = rate;
            bucketAcc = 0;
        }
        long gained() { return (start < 0 || last < 0) ? 0 : Math.max(0, last - start); }
        long[] seriesCopy() { long[] out = new long[filled]; System.arraycopy(series, 0, out, 0, filled); return out; }
    }

    private final Track total = new Track();
    private final Map<Sk, Track> bySkill = new EnumMap<>(Sk.class);
    private long lastSessionMs = 0;
    private long bucketAccMs = 0;
    private boolean started = false;

    /** Sample the XP counters. Call once per loop with the session's ACTIVE playtime. */
    public synchronized void tick(long sessionActiveMs) {
        long[] all = allXp();
        if (all == null) return;

        long sum = 0;
        for (long x : all) sum += Math.max(0, x);

        if (!started) {
            started = true;
            total.begin(sum);
            for (Sk s : Sk.values()) track(s).begin(xpOf(all, s));
            lastSessionMs = sessionActiveMs;
            return;
        }

        long dMs = Math.max(0, sessionActiveMs - lastSessionMs);
        lastSessionMs = sessionActiveMs;

        total.accrue(sum);
        for (Sk s : Sk.values()) track(s).accrue(xpOf(all, s));

        bucketAccMs += dMs;
        if (bucketAccMs >= BUCKET_MS) {
            total.closeBucket(bucketAccMs);
            for (Sk s : Sk.values()) track(s).closeBucket(bucketAccMs);
            bucketAccMs = 0;
        }
    }

    private Track track(Sk s) { return bySkill.computeIfAbsent(s, k -> new Track()); }

    private static long xpOf(long[] all, Sk s) {
        try { int i = s.ordinal(); return (all != null && i < all.length) ? Math.max(0, all[i]) : 0; }
        catch (Throwable t) { return 0; }
    }

    /** Total XP across every skill, or -1 if unavailable. */
    public static long totalXp() {
        long[] all = allXp();
        if (all == null) return -1;
        long sum = 0;
        for (long x : all) sum += Math.max(0, x);
        return sum;
    }

    // ---- Total (all skills) --------------------------------------------------------------------
    public synchronized long sessionXpGained() { return total.gained(); }
    public synchronized long sessionRatePerHour(long sessionActiveMs) { return rate(total.gained(), sessionActiveMs); }
    public synchronized long peakRatePerHour() { return total.peak; }
    public synchronized long[] seriesCopy() { return total.seriesCopy(); }
    public long lifetimeXp() { long t = totalXp(); return t < 0 ? 0 : t; }
    public long lifetimeRatePerHour(long lifetimeActiveMs) { return rate(lifetimeXp(), lifetimeActiveMs); }

    // ---- Per skill -----------------------------------------------------------------------------
    public synchronized long sessionXpGained(Sk s) { return s == null ? 0 : track(s).gained(); }
    public synchronized long sessionRatePerHour(Sk s, long sessionActiveMs) { return s == null ? 0 : rate(track(s).gained(), sessionActiveMs); }
    public synchronized long peakRatePerHour(Sk s) { return s == null ? 0 : track(s).peak; }
    public synchronized long[] seriesCopy(Sk s) { return s == null ? new long[0] : track(s).seriesCopy(); }

    public long lifetimeXp(Sk s) {
        if (s == null) return 0;
        try { GameApi a = g(); return a == null ? 0 : Math.max(0, a.skillXp(s.name())); } catch (Throwable t) { return 0; }
    }
    public long lifetimeRatePerHour(Sk s, long lifetimeActiveMs) { return rate(lifetimeXp(s), lifetimeActiveMs); }

    private static long rate(long xp, long ms) { return ms < 1000 ? 0 : Math.round(xp * 3_600_000.0 / ms); }

    /** "12m" until {@code skill} levels at the current session rate, or "—" when unknown. */
    public String timeToNextLevel(Sk skill, long sessionActiveMs) {
        if (skill == null) return "—";
        long r = sessionRatePerHour(skill, sessionActiveMs);
        if (r <= 0) r = sessionRatePerHour(sessionActiveMs);
        if (r <= 0) return "—";
        int remaining;
        try { GameApi a = g(); remaining = a == null ? 0 : a.xpToLevel(skill.name()); } catch (Throwable t) { return "—"; }
        if (remaining <= 0) return "—";
        long minutes = Math.round(remaining * 60.0 / r);
        if (minutes < 1) return "<1m";
        if (minutes < 60) return minutes + "m";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    /** 1_234_567 → "1.2M". */
    public static String compact(long n) {
        if (n >= 1_000_000_000L) return trim(n / 1_000_000_000.0) + "B";
        if (n >= 1_000_000L) return trim(n / 1_000_000.0) + "M";
        if (n >= 1_000L) return trim(n / 1_000.0) + "K";
        return String.valueOf(n);
    }
    private static String trim(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
    public static String grouped(long n) { return String.format(java.util.Locale.ROOT, "%,d", n); }
}
