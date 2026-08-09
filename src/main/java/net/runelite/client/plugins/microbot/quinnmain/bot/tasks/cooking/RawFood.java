package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.cooking;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * Raw foods we know how to cook, with the raw item ID, the Cooking level to cook it, an XP-rate rank
 * (≈ cooking XP), and the level at which it stops burning on a normal range ({@link #noBurnLevel}).
 * The trainer cooks the food with the best <b>effective</b> XP for the level (rate × how often it cooks
 * rather than burns), which naturally consumes the fish the Fishing trainer banks (shrimp/trout/salmon…).
 *
 * <p>Cooking is fully F2P, so there's no members gate here. Burnt food gives no XP, so a food you just
 * unlocked (and burn most of) can be worse XP/hr than a slightly lower food you rarely burn — that's
 * what {@link #effectiveScore} captures, so the trainer doesn't waste a full inventory on burns.
 *
 * <p><b>Burn-free levels</b> are for a standard range with no burn-reducing bonuses (no cooking gauntlets
 * / cooking guild / Hosidius, which are members anyway); they're approximate (sources vary by ±1–2).
 */
public enum RawFood {
    //        rawName          rawId cooked lvl xpRank noBurn
    BEEF     ("Raw beef",      2132, 2142, 1,  30,    31),
    CHICKEN  ("Raw chicken",   2138, 2140, 1,  30,    31),
    SHRIMPS  ("Raw shrimps",   317,  315,  1,  30,    34),
    ANCHOVIES("Raw anchovies", 321,  319,  1,  34,    34),
    SARDINE  ("Raw sardine",   327,  325,  1,  40,    38),
    HERRING  ("Raw herring",   345,  347,  5,  50,    41),
    MACKEREL ("Raw mackerel",  353,  355,  10, 60,    45),
    TROUT    ("Raw trout",     335,  333,  15, 70,    50),
    COD      ("Raw cod",       341,  339,  18, 75,    52),
    PIKE     ("Raw pike",      349,  343,  20, 80,    53),
    SALMON   ("Raw salmon",    331,  329,  25, 90,    58),
    TUNA     ("Raw tuna",      359,  361,  30, 100,   64),
    LOBSTER  ("Raw lobster",   377,  379,  40, 120,   74),
    BASS     ("Raw bass",      363,  365,  43, 130,   80),
    SWORDFISH("Raw swordfish", 371,  373,  45, 140,   86);

    public final String rawName;
    public final int rawId;
    public final int cookedId;
    public final int cookLevel;
    public final int xpRank;
    /** Cooking level at which this stops burning on a normal range (approximate). */
    public final int noBurnLevel;

    RawFood(String rawName, int rawId, int cookedId, int cookLevel, int xpRank, int noBurnLevel) {
        this.rawName = rawName;
        this.rawId = rawId;
        this.cookedId = cookedId;
        this.cookLevel = cookLevel;
        this.xpRank = xpRank;
        this.noBurnLevel = noBurnLevel;
    }

    /**
     * Rough fraction of this food that cooks (doesn't burn) at {@code cookingLvl} on a normal range:
     * ~0.30 the moment it's unlocked, rising linearly to 1.0 at {@link #noBurnLevel}. A coarse model, but
     * enough to stop the trainer picking a food it burns most of over one it barely burns.
     */
    public double successRate(int cookingLvl) {
        if (cookingLvl >= noBurnLevel) return 1.0;
        if (cookingLvl <= cookLevel || noBurnLevel <= cookLevel) return 0.30;
        double f = (double) (cookingLvl - cookLevel) / (noBurnLevel - cookLevel);
        return 0.30 + 0.70 * Math.max(0.0, Math.min(1.0, f));
    }

    /** XP-rate weighted by how often it actually cooks (vs burns) at this level — the pick metric. */
    public double effectiveScore(int cookingLvl) {
        return xpRank * successRate(cookingLvl);
    }
}
