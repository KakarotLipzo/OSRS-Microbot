package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeMap;

/**
 * The per-day <b>active-playtime</b> log — one row per calendar day, hours of active play (breaks
 * and logout time excluded), persisted to {@code <username>_runlog.properties}.
 *
 * <p>This is the single source the HUD's "Hours per day" chart reads. It's fed the same active-ms
 * delta the {@link PlaytimeTracker} accrues from, so a bar is genuine training time — never
 * wall-clock. The store is append-only (a decade is a few KB), sorted by date, and zero-fills the
 * gaps between recorded days so the chart can show days off as it likes.
 *
 * <p><b>Note on "Lifetime":</b> this log only holds days since logging began. The HUD's cumulative
 * lifetime figure keeps coming from {@link PlaytimeTracker} / {@link ConfigStore} (which carries a
 * base that predates this log), so the chart's per-day bars and the HUD's lifetime total measure
 * different-but-honest things — the bars never claim runtime for days they have no data for.
 */
public class RunLog {

    private static final double MS_PER_HOUR = 3_600_000d;

    /** date → active hours that day. Sorted, gaps implied (absent = 0h). */
    private final TreeMap<LocalDate, Double> days = new TreeMap<>();
    private File file;
    private boolean loaded = false;
    private LocalDate today;
    private long dirtyMs = 0;   // active ms accrued but not yet folded into `today` (for logging only)

    /** Load this account's run log once the username is known. Idempotent. */
    public synchronized void loadFor(String username) {
        if (loaded) return;
        String u = username == null ? "unknown"
                : username.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9._-]", "_");
        File dir = new File(System.getProperty("user.home"),
                "DreamBot" + File.separator + "QuinnMain");
        dir.mkdirs();
        file = new File(dir, u + "_runlog.properties");

        if (file.exists()) {
            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream(file)) {
                p.load(in);
            } catch (Exception e) {
                Log.log("[runlog] load failed: " + e);
            }
            for (String key : p.stringPropertyNames()) {
                try {
                    LocalDate d = LocalDate.parse(key);
                    double h = Double.parseDouble(p.getProperty(key));
                    if (h > 0) days.merge(d, h, Double::sum);
                } catch (Exception ignored) { /* skip malformed rows */ }
            }
        }
        today = LocalDate.now();
        loaded = true;
        Log.log("[runlog] loaded " + days.size() + " day(s), "
                + String.format(Locale.US, "%.1f", lifetimeHours()) + "h logged.");
    }

    /**
     * Fold this tick's active milliseconds into today's bucket. Pass the same delta
     * {@link PlaytimeTracker#update(boolean)} returned, so breaks (delta 0) never count.
     */
    public synchronized void accrue(long activeDeltaMs) {
        if (!loaded || activeDeltaMs <= 0) return;
        LocalDate now = LocalDate.now();
        if (today == null || !now.equals(today)) today = now;   // rolled past local midnight → new bucket
        days.merge(today, activeDeltaMs / MS_PER_HOUR, Double::sum);
        dirtyMs += activeDeltaMs;
    }

    /** Persist atomically (temp file + move) so a crash mid-write can't corrupt the log. */
    public synchronized void save() {
        if (file == null) return;
        Properties p = new Properties();
        for (var e : days.entrySet()) {
            p.setProperty(e.getKey().toString(), String.format(Locale.US, "%.4f", e.getValue()));
        }
        try {
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                p.store(out, "QuinnMain per-day active hours (ISO-date=hours). Append-only.");
            }
            try {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFailed) {
                // Some filesystems reject ATOMIC_MOVE across a rename-with-replace; fall back plainly.
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dirtyMs = 0;
        } catch (Exception e) {
            Log.log("[runlog] save failed: " + e);
        }
    }

    // ---- Series the chart reads ---------------------------------------------------------------
    // The series is a CONTINUOUS run of calendar days from the first recorded day to today, with
    // gaps zero-filled, oldest first. The chart slices/buckets it client-side (30d/90d/1y/All).

    private List<LocalDate> seriesDates() {
        List<LocalDate> out = new ArrayList<>();
        LocalDate end = today != null ? today : LocalDate.now();
        LocalDate start = days.isEmpty() ? end : days.firstKey();
        if (start.isAfter(end)) start = end;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) out.add(d);
        return out;
    }

    /** Active hours per day, oldest→newest, zero-filled for days off (includes today). */
    public synchronized double[] hoursSeries() {
        List<LocalDate> ds = seriesDates();
        double[] out = new double[ds.size()];
        for (int i = 0; i < ds.size(); i++) out[i] = days.getOrDefault(ds.get(i), 0d);
        return out;
    }

    /** ISO date strings matching {@link #hoursSeries()}. */
    public synchronized String[] dateSeries() {
        List<LocalDate> ds = seriesDates();
        String[] out = new String[ds.size()];
        for (int i = 0; i < ds.size(); i++) out[i] = ds.get(i).toString();
        return out;
    }

    /** Total hours ever logged (sum of every recorded day). */
    public synchronized double lifetimeHours() {
        double sum = 0;
        for (double h : days.values()) sum += h;
        return sum;
    }

    /** Hours on the single most active day, or 0 if nothing is logged. */
    public synchronized double busiestDay() {
        double max = 0;
        for (double h : days.values()) max = Math.max(max, h);
        return max;
    }

    /** Days with any runtime (the log's non-zero rows). */
    public synchronized int daysRun() { return days.size(); }

    /** Calendar days spanned from the first recorded day to today (the series length). */
    public synchronized int daysElapsed() { return seriesDates().size(); }

    /** Whether anything has been logged yet — the chart shows an empty state otherwise. */
    public synchronized boolean isEmpty() { return days.isEmpty(); }

    /**
     * Testing hook for the offline preview harness: replace the log with a synthetic series ending
     * today, so the chart can be rendered without a game client. Not used in production.
     */
    public synchronized void seedForPreview(double[] hours) {
        days.clear();
        LocalDate end = LocalDate.now();
        int n = hours.length;
        for (int i = 0; i < n; i++) {
            if (hours[i] > 0) days.put(end.minusDays(n - 1 - i), hours[i]);
        }
        today = end;
        loaded = true;
    }
}
