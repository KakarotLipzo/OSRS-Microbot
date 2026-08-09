package net.runelite.client.plugins.microbot.quinnmain.bot.core;

import net.runelite.client.plugins.microbot.quinnmain.game.Game;
import net.runelite.client.plugins.microbot.quinnmain.game.GameApi;
import net.runelite.client.plugins.microbot.quinnmain.game.Sk;

/**
 * Thin read-only view over the account's live state: login status, username, membership, and skill
 * levels. Ported from OSRS-Main; the DreamBot Client/Skills/Quests/Varcs statics are now read through
 * the {@link GameApi} facade via the {@link Game} holder.
 */
public class AccountState {

    private static GameApi g() { return Game.api(); }

    public boolean isLoggedIn() {
        try { return g() != null && g().isLoggedIn(); } catch (Throwable e) { return false; }
    }

    public String username() {
        try { return g() == null ? null : g().username(); } catch (Throwable e) { return null; }
    }

    /** True only when confident, so an F2P account is never mistaken for members. */
    public boolean isMembers() {
        try { return g() != null && g().isMembers(); } catch (Throwable e) { return false; }
    }

    public int level(Sk s) {
        try { return g() == null ? 1 : g().skillLevelReal(s.name()); } catch (Throwable e) { return 1; }
    }

    public int totalLevel() {
        try { return g() == null ? 0 : g().totalLevel(); } catch (Throwable e) { return 0; }
    }

    public int questPoints() {
        try { return g() == null ? 0 : g().questPoints(); } catch (Throwable e) { return 0; }
    }

    /**
     * Total time played in minutes (Character Summary figure, VarClientInt 526) — the 20-hour leg of
     * the new-account GE trade restriction. 0 until the varc is populated.
     */
    public int timePlayedMinutes() {
        try { return g() == null ? 0 : Math.max(0, g().varcInt(526)); } catch (Throwable e) { return 0; }
    }

    public boolean atOrAboveTarget(Sk s, int target) { return level(s) >= target; }

    /** Fraction (0..1) through the current level by XP — what the HUD pill bar draws. */
    public double progressToNextLevel(Sk s) {
        try {
            int lvl = level(s);
            if (lvl >= 99) return 1.0;
            long start = experienceForLevel(lvl);
            long next = experienceForLevel(lvl + 1);
            long span = next - start;
            if (span <= 0) return 0;
            long xp = g() == null ? start : g().skillXp(s.name());
            double p = (xp - start) / (double) span;
            return Math.max(0, Math.min(1, p));
        } catch (Throwable e) { return 0; }
    }

    /** OSRS cumulative XP required to reach {@code level} (standard 1..99 curve). */
    public static long experienceForLevel(int level) {
        if (level <= 1) return 0;
        double points = 0;
        for (int n = 1; n < level; n++) points += Math.floor(n + 300 * Math.pow(2, n / 7.0));
        return (long) Math.floor(points / 4.0);
    }
}
