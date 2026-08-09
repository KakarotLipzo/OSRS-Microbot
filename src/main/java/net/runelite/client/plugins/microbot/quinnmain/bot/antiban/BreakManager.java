package net.runelite.client.plugins.microbot.quinnmain.bot.antiban;

import net.runelite.client.plugins.microbot.quinnmain.bot.core.BreakRule;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.Nav;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.TaskContext;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;
import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;

/**
 * Break &amp; AFK scheduler, driven by the Breaks config. Ported from OSRS-Main to the {@link GameApi}
 * facade. Two pause kinds (per-cycle break rules + micro-AFK), plus schedule enforcement (active hours,
 * daily budget, longest session, sleep break, days off).
 *
 * <p><b>Login handling difference vs DreamBot.</b> DreamBot's LOGIN random-solver toggle has no direct
 * Microbot equivalent, so {@link #setLoginSolver} is a documented no-op here; logout/login go through
 * the facade ({@code game.logout()}/{@code game.login()}), which on Microbot uses its own login layer.
 */
public class BreakManager {

    private final Random rng = new Random();

    private long nextBreakAtActiveMs;
    private long nextAfkAtActiveMs;
    private long pendingRestMs;
    private boolean onBreak = false;
    private boolean breakWasLogout = false;
    private long breakEndsWall = 0;
    private boolean bankedThisBreak = false;

    private long lastSessionMs = 0;
    private long dayActiveMs = 0;
    private long dayBudgetMs = 0;
    private boolean dayOff = false;
    private boolean sleptToday = false;
    private LocalDate currentDay = LocalDate.now();
    private long lastBreakEndActiveMs = 0;
    private volatile boolean stopRequested = false;
    private volatile boolean rerollAfterBreak = false;
    private boolean dayRolled = false;

    private static GameApi g() { return Game.api(); }

    public BreakManager() {
        scheduleNextBreak(0, null);
        scheduleNextAfk(0);
        rollDay();
    }

    // ── scheduling ──────────────────────────────────────────────────────────────────────────
    private void scheduleNextBreak(long activeNow, List<BreakRule> rules) {
        BreakRule rule = pickRule(rules);
        if (rule != null) {
            nextBreakAtActiveMs = activeNow + jitter(rule.playMin, rule.jitterPct) * 60_000L;
            pendingRestMs = jitter(rule.restMin, rule.jitterPct) * 60_000L;
        } else {
            nextBreakAtActiveMs = activeNow + (20 + rng.nextInt(36)) * 60_000L;
            pendingRestMs = (3 + rng.nextInt(13)) * 60_000L;
        }
    }

    private BreakRule pickRule(List<BreakRule> rules) {
        if (rules == null) return null;
        java.util.List<BreakRule> on = new java.util.ArrayList<>();
        for (BreakRule r : rules) if (r.on && r.playMin > 0 && r.restMin > 0) on.add(r);
        return on.isEmpty() ? null : on.get(rng.nextInt(on.size()));
    }

    private int jitter(int baseMin, int jitterPct) {
        if (jitterPct <= 0) return Math.max(1, baseMin);
        double f = 1.0 + (rng.nextDouble() * 2 - 1) * (jitterPct / 100.0);
        return Math.max(1, (int) Math.round(baseMin * f));
    }

    private void scheduleNextAfk(long activeNow) { nextAfkAtActiveMs = activeNow + (3 + rng.nextInt(9)) * 60_000L; }

    private void rollDay() {
        dayActiveMs = 0; sleptToday = false; currentDay = LocalDate.now();
        dayBudgetMs = -1; dayOff = false; dayRolled = false;
    }

    // ── public state ────────────────────────────────────────────────────────────────────────
    public boolean isOnBreak() { return onBreak; }
    public void restoreLogin() { setLoginSolver(true); }
    public boolean shouldStop() { return stopRequested; }
    public boolean consumeRerollAfterBreak() { if (rerollAfterBreak) { rerollAfterBreak = false; return true; } return false; }
    public long msUntilBreak(long sessionActiveMs) { return onBreak ? 0 : Math.max(0, nextBreakAtActiveMs - sessionActiveMs); }
    public long msLeftOfBreak() { return onBreak ? Math.max(0, breakEndsWall - System.currentTimeMillis()) : 0; }

    // ── main tick ───────────────────────────────────────────────────────────────────────────
    public boolean handle(TaskContext ctx, long sessionActiveMs) {
        GameApi a = g();
        long now = System.currentTimeMillis();

        if (!ctx.config.isBreaksEnabled() && !onBreak) return false;

        if (!LocalDate.now().equals(currentDay)) rollDay();
        if (!dayRolled) { computeDayBudget(ctx); dayRolled = true; }

        long delta = Math.max(0, sessionActiveMs - lastSessionMs);
        lastSessionMs = sessionActiveMs;
        if (!onBreak) dayActiveMs += delta;

        if (onBreak) {
            if (now < breakEndsWall) return true;
            if (breakWasLogout && a != null && !a.isLoggedIn()) { if (!logBackIn()) return true; }
            onBreak = false; breakWasLogout = false; bankedThisBreak = false;
            lastBreakEndActiveMs = sessionActiveMs;
            if (!ctx.config.isBreakResumeSame()) rerollAfterBreak = true;
            scheduleNextBreak(sessionActiveMs, ctx.config.getBreakRules());
            scheduleNextAfk(sessionActiveMs);
            Log.log("[break] break finished — resuming play.");
            return true;
        }

        if (dayOff) return startLogoutBreak(ctx, msUntilNextLocalDay(), "day off");
        long untilOpen = msUntilWindowOpens(ctx);
        if (untilOpen > 0) return startLogoutBreak(ctx, untilOpen, "outside active hours");
        if (dayBudgetMs > 0 && dayActiveMs >= dayBudgetMs) {
            if (ctx.config.isBreakStopWhenDone()) {
                stopRequested = true;
                Log.log("[break] daily play budget reached (" + (dayBudgetMs / 3_600_000) + "h) — stopping for the day.");
                return true;
            }
            return startLogoutBreak(ctx, msUntilNextLocalDay(), "daily budget spent");
        }
        if (ctx.config.isBreakSleepOn() && !sleptToday && dayActiveMs >= sleepAfterMs(ctx)) {
            sleptToday = true;
            return startLogoutBreak(ctx, ctx.config.getBreakSleepHours() * 3_600_000L, "sleep break");
        }
        int maxH = ctx.config.getBreakMaxSessionHours();
        if (maxH > 0 && sessionActiveMs - lastBreakEndActiveMs >= maxH * 3_600_000L)
            return startConfiguredBreak(ctx, sessionActiveMs, "longest-session cap");

        if (sessionActiveMs >= nextBreakAtActiveMs) return startConfiguredBreak(ctx, sessionActiveMs, "scheduled break");

        if (sessionActiveMs >= nextAfkAtActiveMs) {
            if (safeToPause()) {
                int dur = 5_000 + rng.nextInt(40_000);
                Log.log("[afk] safe-area micro-AFK for " + (dur / 1000) + "s.");
                if (a != null) a.sleep(dur);
                scheduleNextAfk(sessionActiveMs);
                return true;
            }
            return false;
        }
        return false;
    }

    // ── starting breaks ─────────────────────────────────────────────────────────────────────
    private boolean startConfiguredBreak(TaskContext ctx, long sessionActiveMs, String reason) {
        long dur = pendingRestMs > 0 ? pendingRestMs : (3 + rng.nextInt(13)) * 60_000L;
        return ctx.config.isBreakLogout() ? startLogoutBreak(ctx, dur, reason) : startAfkBreak(ctx, dur, reason);
    }

    private boolean startLogoutBreak(TaskContext ctx, long durMs, String reason) {
        GameApi a = g();
        if (a == null || !safeToPause()) return false;

        if (ctx.config.isBreakBankFirst() && !bankedThisBreak) {
            if (!a.bankIsOpen()) { if (!Nav.openBank(null)) return true; return true; }
            a.depositInventory();
            a.waitUntil(() -> a.invEmptySlots() >= 28, 2000);
            a.closeBank();
            bankedThisBreak = true;
            Log.log("[break] banked before the break.");
            return true;
        }

        Log.log("[break] taking a " + fmtDur(durMs) + " break (logout) — " + reason + ".");
        setLoginSolver(false);
        try {
            a.logout();
            a.waitUntil(() -> !a.isLoggedIn(), 5000);
        } catch (Throwable e) {
            setLoginSolver(true);
            Log.log("[break] logout failed (will retry): " + e);
            return false;
        }
        if (a.isLoggedIn()) { Log.log("[break] logout didn't take — retrying next tick."); return false; }
        onBreak = true; breakWasLogout = true; breakEndsWall = System.currentTimeMillis() + durMs;
        Log.log("[break] logged out — break for " + fmtDur(durMs) + ".");
        return true;
    }

    private boolean startAfkBreak(TaskContext ctx, long durMs, String reason) {
        if (!safeToPause()) return false;
        Log.log("[break] taking a " + fmtDur(durMs) + " AFK break (staying online) — " + reason + ".");
        onBreak = true; breakWasLogout = false; breakEndsWall = System.currentTimeMillis() + durMs;
        return true;
    }

    // ── schedule helpers ────────────────────────────────────────────────────────────────────
    private void computeDayBudget(TaskContext ctx) {
        int h = ctx.config.getBreakHoursPerDay();
        if (h <= 0) dayBudgetMs = 0;
        else {
            int vary = ctx.config.getBreakVaryPct();
            double f = vary <= 0 ? 1.0 : 1.0 + (rng.nextDouble() * 2 - 1) * (vary / 100.0);
            dayBudgetMs = Math.max(30 * 60_000L, (long) (h * 3_600_000L * f));
        }
        int daysOff = ctx.config.getBreakDaysOff();
        dayOff = daysOff > 0 && rng.nextInt(7) < daysOff;
        if (dayOff) Log.log("[break] today is a rest day — staying logged out.");
    }

    private long sleepAfterMs(TaskContext ctx) { return dayBudgetMs > 0 ? (long) (dayBudgetMs * 0.9) : 10 * 3_600_000L; }

    private long msUntilWindowOpens(TaskContext ctx) {
        LocalTime from = parseTime(ctx.config.getBreakActiveFrom());
        LocalTime to = parseTime(ctx.config.getBreakActiveTo());
        if (from == null || to == null || from.equals(to)) return 0;
        LocalTime now = LocalTime.now();
        boolean inside = from.isBefore(to)
                ? (!now.isBefore(from) && now.isBefore(to))
                : (!now.isBefore(from) || now.isBefore(to));
        if (inside) return 0;
        int mins = (from.toSecondOfDay() - now.toSecondOfDay()) / 60;
        if (mins <= 0) mins += 24 * 60;
        return mins * 60_000L;
    }

    private long msUntilNextLocalDay() {
        LocalTime now = LocalTime.now();
        int secsLeft = 24 * 3600 - now.toSecondOfDay();
        return Math.max(60_000L, secsLeft * 1000L);
    }

    private static LocalTime parseTime(String hhmm) {
        if (hhmm == null || hhmm.trim().isEmpty()) return null;
        try {
            String[] p = hhmm.trim().split(":");
            int h = Integer.parseInt(p[0].trim()), m = p.length > 1 ? Integer.parseInt(p[1].trim()) : 0;
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
            return LocalTime.of(h, m);
        } catch (Exception e) { return null; }
    }

    private static String fmtDur(long ms) {
        long mins = ms / 60_000;
        if (mins < 60) return mins + " min";
        long h = mins / 60, m = mins % 60;
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }

    // ── mechanics ───────────────────────────────────────────────────────────────────────────
    private boolean logBackIn() {
        GameApi a = g();
        if (a == null) return false;
        if (a.isLoggedIn()) return true;
        try { a.login(); Log.log("[break] logging back in after the break…"); }
        catch (Throwable e) { Log.log("[break] re-login attempt errored (will retry): " + e); }
        a.waitUntil(a::isLoggedIn, 8000);
        return a.isLoggedIn();
    }

    /** No-op on Microbot (no DreamBot LOGIN random-solver). Login handling is the facade's job. */
    private void setLoginSolver(boolean enabled) { /* TODO: Microbot has no equivalent solver toggle. */ }

    /** SAFE to pause = not in combat and no attackable NPC within 2 tiles. Any uncertainty → false. */
    public boolean safeToPause() {
        GameApi a = g();
        if (a == null) return false;
        try {
            if (a.isInCombat()) return false;
            for (GameApi.Npc n : a.npcsWithin(2)) {
                if (n != null && n.hasAction("Attack") && n.distance() <= 2) return false;
            }
            return true;
        } catch (Throwable e) { return false; }
    }
}
