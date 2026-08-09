package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * Tracks <b>active playing time</b> — wall-clock time while the character is logged in and
 * actually playing, <i>excluding</i> break/logout time. Exposes a per-session total and a
 * cumulative lifetime total (session + a base loaded from {@link ConfigStore}).
 *
 * <p>{@link #update(boolean)} is called once per loop with whether we are "playing" right now;
 * it accrues the elapsed time since the previous tick only when playing, and returns the delta
 * so callers (e.g. the {@link GoalEngine} 4h-per-skill cap) can accrue against it too.
 */
public class PlaytimeTracker {

    /** Ignore a single tick's gap larger than this (clock jump, or resuming after a break). */
    private static final long MAX_TICK_GAP_MS = 60_000L;

    private long sessionMs = 0;
    private long lifetimeBaseMs = 0;
    private boolean baseSet = false;
    private long lastTick = -1;

    /** Set the lifetime base once, from the persisted config value. Later calls are ignored. */
    public void setLifetimeBase(long ms) {
        if (!baseSet) {
            lifetimeBaseMs = Math.max(0, ms);
            baseSet = true;
        }
    }

    public boolean isBaseSet() { return baseSet; }

    /**
     * @param playing true if logged in and not on a logout break.
     * @return active milliseconds added this tick (0 when paused or on the first/oversized tick).
     */
    public long update(boolean playing) {
        long now = System.currentTimeMillis();
        if (lastTick < 0) { lastTick = now; return 0; }
        long delta = now - lastTick;
        lastTick = now;
        if (!playing || delta <= 0 || delta > MAX_TICK_GAP_MS) return 0;
        sessionMs += delta;
        return delta;
    }

    public long sessionMs() { return sessionMs; }
    public long lifetimeMs() { return lifetimeBaseMs + sessionMs; }

    public static String format(long ms) {
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }
}
