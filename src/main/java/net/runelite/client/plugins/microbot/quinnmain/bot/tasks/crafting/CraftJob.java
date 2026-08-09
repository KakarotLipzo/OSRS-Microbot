package net.runelite.client.plugins.microbot.quinnmain.bot.tasks.crafting;

import net.runelite.client.plugins.microbot.quinnmain.game.Sk;
import net.runelite.client.plugins.microbot.quinnmain.game.Pos;
import net.runelite.client.plugins.microbot.quinnmain.bot.core.BankLoc;
import net.runelite.client.plugins.microbot.quinnmain.bot.util.Log;

/**
 * A single craftable recipe within a {@link CraftMethod}: the tool (kept), an optional consumable
 * (e.g. thread), the primary material (consumed, GE-bought when the bank is empty), the product, the
 * Crafting level, and an XP-per-item rank. In AUTO mode the trainer picks the highest-XP job its level
 * allows across <i>all</i> methods (preferring one it can source now); pinned to a method, it picks the
 * highest-level job of that method.
 *
 * <p>F2P ladder (OSRS Wiki "Free-to-play Crafting training"): leather items 1–28 (cheapest, leather from
 * cows), hardleather body 28+ (cheap filler), gold ring + cut gems sapphire→diamond (faster, costlier).
 * Gem <i>jewellery</i> (two materials: gold bar + cut gem) isn't modelled yet — the single-primary recipe
 * shape here covers the cut-gem and leather paths.
 */
public enum CraftJob {
    // method            productName        toolId consum primaryId primaryName     productId lvl  fallback xpRank
    // Leather (needle + thread on leather) — the cheap 1–28 path.
    LEATHER_GLOVES  (CraftMethod.LEATHER, "Leather gloves",    1733, 1734, 1741, "Leather",      1059, 1,  40,  14),
    LEATHER_BOOTS   (CraftMethod.LEATHER, "Leather boots",     1733, 1734, 1741, "Leather",      1061, 7,  40,  16),
    LEATHER_COWL    (CraftMethod.LEATHER, "Leather cowl",      1733, 1734, 1741, "Leather",      1167, 9,  40,  18),
    LEATHER_VAMBS   (CraftMethod.LEATHER, "Leather vambraces", 1733, 1734, 1741, "Leather",      1063, 11, 40,  22),
    LEATHER_BODY    (CraftMethod.LEATHER, "Leather body",      1733, 1734, 1741, "Leather",      1129, 14, 40,  25),
    LEATHER_CHAPS   (CraftMethod.LEATHER, "Leather chaps",     1733, 1734, 1741, "Leather",      1095, 18, 40,  27),
    HARDLEATHER_BODY(CraftMethod.LEATHER, "Hardleather body",  1733, 1734, 1743, "Hard leather", 1131, 28, 60,  35),

    // Gold jewellery (ring mould + gold bar at a furnace).
    GOLD_RING       (CraftMethod.GOLD_JEWELRY, "Gold ring",    1592, 0,    2357, "Gold bar",     1635, 5,  110, 15),

    // Cut gems (chisel on uncut gem) — the faster, costlier path.
    CUT_SAPPHIRE    (CraftMethod.GEMS, "Sapphire",             1755, 0,    1623, "Uncut sapphire",1607, 20, 350, 50),
    CUT_EMERALD     (CraftMethod.GEMS, "Emerald",              1755, 0,    1621, "Uncut emerald", 1605, 27, 600, 67),
    CUT_RUBY        (CraftMethod.GEMS, "Ruby",                 1755, 0,    1619, "Uncut ruby",    1603, 34, 1000,85),
    CUT_DIAMOND     (CraftMethod.GEMS, "Diamond",              1755, 0,    1617, "Uncut diamond", 1601, 43, 2200,108);

    public final CraftMethod method;
    public final String productName;
    public final int toolId;
    public final int consumableId; // 0 = none
    public final int primaryId;
    public final String primaryName;
    public final int productId;
    public final int craftLevel;
    public final int primaryFallbackPrice;
    /** Approximate crafting XP per item — the AUTO-mode ranking metric. */
    public final int xpRank;

    CraftJob(CraftMethod method, String productName, int toolId, int consumableId,
             int primaryId, String primaryName, int productId, int craftLevel,
             int primaryFallbackPrice, int xpRank) {
        this.method = method;
        this.productName = productName;
        this.toolId = toolId;
        this.consumableId = consumableId;
        this.primaryId = primaryId;
        this.primaryName = primaryName;
        this.productId = productId;
        this.craftLevel = craftLevel;
        this.primaryFallbackPrice = primaryFallbackPrice;
        this.xpRank = xpRank;
    }

    /** Inventory slots reserved for the tool (+ consumable); the rest fill with the primary material. */
    public int reservedSlots() { return 1 + (consumableId != 0 ? 1 : 0); }
}
